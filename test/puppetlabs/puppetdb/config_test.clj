(ns puppetlabs.puppetdb.config-test
  (:require
   [clojure.test :refer :all]
   [puppetlabs.kitchensink.core :as kitchensink]
   [puppetlabs.puppetdb.config :as conf
    :refer [configure-command-processing
            configure-dbs
            configure-puppetdb
            convert-blocklist-config
            default-max-command-size
            forbid-duplicate-write-db-subnames
            half-the-cores*
            normalize-product-name
            validate-vardir
            warn-retirements]]
   [puppetlabs.puppetdb.nio :refer [get-path]]
   [puppetlabs.puppetdb.testutils :refer [with-caught-ex-info]]
   [puppetlabs.puppetdb.testutils.nio :refer [call-with-temp-dir-path]]
   [puppetlabs.puppetdb.time :as time]
   [puppetlabs.puppetdb.testutils.db :refer [sample-db-config]]
   [clojure.string :as str]
   [me.raynes.fs :as fs])
  (:import
   (clojure.lang ExceptionInfo)
   (java.util.regex Pattern)))

(deftest puppetdb-configuration
  (testing "puppetdb-configuration"
    (testing "should default :add-agent-report-filter to true"
      (let [config (configure-puppetdb {})]
        (is (= true (get-in config [:puppetdb :add-agent-report-filter])))))

    (testing "should allow add-agent-report-filter boolean"
      (let [config (configure-puppetdb {:puppetdb {:add-agent-report-filter "true"}})]
        (is (= true (get-in config [:puppetdb :add-agent-report-filter]))))
      (let [config (configure-puppetdb {:puppetdb {:add-agent-report-filter "false"}})]
        (is (= false (get-in config [:puppetdb :add-agent-report-filter]))))
      (let [config (configure-puppetdb {:puppetdb {:add-agent-report-filter "some-string"}})]
        (is (= false (get-in config [:puppetdb :add-agent-report-filter]))))
      (is (thrown? clojure.lang.ExceptionInfo
                   (configure-puppetdb {:puppetdb {:add-agent-report-filter 1337}}))))

    (testing "should default :log-queries to false"
      (let [config (configure-puppetdb {})]
        (is (= false (get-in config [:puppetdb :log-queries])))))

    (testing "should allow log-user-queries boolean"
      (let [config (configure-puppetdb {:puppetdb {:log-queries "true"}})]
        (is (= true (get-in config [:puppetdb :log-queries]))))
      (let [config (configure-puppetdb {:puppetdb {:log-queries "false"}})]
        (is (= false (get-in config [:puppetdb :log-queries]))))
      (let [config (configure-puppetdb {:puppetdb {:log-queries "some-string"}})]
        (is (= false (get-in config [:puppetdb :log-queries]))))
      (is (thrown? clojure.lang.ExceptionInfo
                   (configure-puppetdb {:puppetdb {:log-queries 1337}}))))))

(deftest commandproc-configuration
  (testing "should use the thread value specified"
    (let [config (configure-command-processing {:command-processing {:threads 37}})]
      (is (= (get-in config [:command-processing :threads]) 37))))
  (let [with-ncores (fn [cores]
                      (with-redefs [kitchensink/num-cpus (constantly cores)]
                        (half-the-cores*)))]
    (testing "should default to half the available CPUs"
      (is (= (with-ncores 4) 2)))
    (testing "should default to half the available CPUs, rounding down"
      (is (= (with-ncores 5) 2)))
    (testing "should default to half the available CPUs, even on single core boxes"
      (is (= (with-ncores 1) 1)))))

(deftest blocklist-regex-validates-and-returns-patterns
  (is (every? #(instance? Pattern %)
              (-> {:subname "bar"
                   :facts-blocklist-type "regex"
                   :facts-blocklist ["^foo$" "bar.*" "b[a].?z"]}
                  convert-blocklist-config
                  (get-in [:database :facts-blocklist]))))

  (let [ex (with-caught-ex-info
             (-> {:subname "bar"
                  :facts-blocklist-type "regex"
                  :facts-blocklist ["^foo[" "(bar.*"]}
                 convert-blocklist-config))]
    (is (= ::conf/cli-error (:type (ex-data ex))))
    (is (re-find #"Unclosed character class near index 4\n"
                 (.getMessage ex)))))

(deftest blocklist-conversion-is-no-op-when-type-literal
  ;; with blocklist-type set to literal all blocklist entries kept as literal strings
  (is (= ["^foo$" "bar.*" "b[a].?z"]
         (-> {:subname "bar"
              :facts-blocklist-type "literal"
              :facts-blocklist ["^foo$" "bar.*" "b[a].?z"]}
             convert-blocklist-config
             :facts-blocklist))))

(deftest blocklist-type-only-accepts-literal-regex-as-values
  (let [config-db (fn [bl-type]
                    (-> {:database {:user "x" :password "?"
                                    :subname "stuff"
                                    :facts-blocklist-type bl-type}}
                        configure-dbs))]

    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Value does not match schema"
                          (config-db "foo")))

    (is (= "regex" (get-in (config-db "regex")
                           [:database :facts-blocklist-type])))

    (is (= "literal" (get-in (config-db "literal")
                             [:database :facts-blocklist-type])))))

(deftest blocklist-type-defaults-to-literal
  (let [config (-> {:database {:user "x" :password "?"
                               :subname "stuff"}}
                   configure-dbs)]
    (is (= (get-in config [:database :facts-blocklist-type]) "literal"))))

(deftest blocklist-converted-correctly-with-ini-and-conf-files
  (let [build-config (fn [x] (-> {:database {:user "x" :password "?"
                                             :subname "stuff"
                                             :facts-blocklist x}}
                                 configure-dbs))
        ini-config (build-config "fact1, fact2, fact3")
        hocon-config (build-config ["fact1" "fact2" "fact3"])]
    (is (= (get-in ini-config [:database :facts-blocklist]) ["fact1" "fact2" "fact3"]))
    (is (= (get-in hocon-config [:database :facts-blocklist]) ["fact1" "fact2" "fact3"]))))

(deftest obsolete-config-redirections
  (call-with-temp-dir-path
   (get-path "target")
   (str *ns*)
   (fn [vardir]
     (doseq [[obsolete replacement val]
             [[[:database :facts-blacklist] [:database :facts-blocklist] ["x"]]
              [[:database :facts-blacklist-type] [:database :facts-blocklist-type] "literal"]
              [[:puppetdb :certificate-whitelist] [:puppetdb :certificate-allowlist] "x, y"]]
             :let [config {:global {:vardir (str vardir)}
                           :database {:user "x" :password "?" :subname "foo"}}]]
       (testing (str "redundant config settings rejected: " obsolete " " replacement)
         (is (thrown-with-msg?
              clojure.lang.ExceptionInfo #"^Configuration specifies both"
              (conf/process-config! (-> config
                                        (assoc-in obsolete val)
                                        (assoc-in replacement val))))))
       (testing (str "obsolete config setting redirected: " obsolete "->" replacement)
         (let [config (conf/process-config! (assoc-in config obsolete val))]
           (is (= nil (get-in config obsolete)))
           (is (= val (get-in config replacement)))))))))

(deftest write-databases-behavior
  (is (= {"default" {:subname "x" ::conf/unnamed true}}
         (conf/write-databases {:database {:subname "x"}})))
  (is (= {"primary" {:subname "y"}
          "secondary" {:subname "z"}}
         (conf/write-databases
          {:database {:subname "x"}
           :database-primary {:subname "y"}
           :database-secondary {:subname "z"}}))))

(deftest database-subname-required
  (let [ex (with-caught-ex-info
             (configure-dbs {:database {:user "x" :password "?"}}))]
    (is (= ::conf/cli-error (:type (ex-data ex))))
    (is (re-find #"No subname set" (.getMessage ex)))))

(deftest duplicate-write-db-subnames-forbidden
  (let [config (-> {:database {:user "x" :password "?" :subname "stuff"}}
                   configure-dbs)]
    (is (= config (forbid-duplicate-write-db-subnames config))))
  (let [config (-> {:database {:user "x" :password "?" :subname "stuff"}
                    :database-x {:subname "stuff"}
                    :database-y {:subname "other-stuff"}}
                   configure-dbs)]
    (is (= config (forbid-duplicate-write-db-subnames config))))
  (let [config {:database {:user "x" :password "?" :subname "stuff"}
                :database-x {:subname "stuff"}
                :database-y {:subname "stuff"}}
        ex (with-caught-ex-info (configure-dbs config))]
    (is (= ::conf/cli-error (:type (ex-data ex))))
    (is (re-find #"^Cannot have duplicate write" (.getMessage ex)))))

(deftest resource-events-and-reports-ttl-disorder
  (let [cfg {:database {:user "x" :password "?" :subname "stuff"}}]
    (let [ex (with-caught-ex-info
               (configure-dbs
                (assoc-in cfg [:database :resource-events-ttl] "15d")))]
      (is (= ::conf/cli-error (:type (ex-data ex))))
      (is (re-find #"The \"database\" resource-events-ttl must not be longer than report-ttl"
                   (.getMessage ex))))
    (let [ex (with-caught-ex-info
               (configure-dbs
                (-> cfg
                    (assoc-in [:database :report-ttl] "3d")
                    (assoc-in [:database :resource-events-ttl] "5d"))))]
      (is (= ::conf/cli-error (:type (ex-data ex))))
      (is (re-find #"The \"database\" resource-events-ttl must not be longer than report-ttl"
                   (.getMessage ex))))))

(deftest database-configuration
  (testing "database"

    (testing "the read-db defaulted to the specified write-db"
      (let [config (-> {:database {:user "x" :password "?" :subname "stuff"}}
                       configure-dbs)]
        (is (= "stuff" (get-in config [:read-database :subname])))))

    (testing "the read-db should be specified by a read-database property"
      (let [base {:user "x" :password "?"}
            config (-> {:database (assoc base :subname "wronger")
                        :read-database (assoc base :subname "stuff")}
                       configure-dbs)]
        (is (= "stuff" (get-in config [:read-database :subname])))))

    (testing "max-pool-size defaults to 25"
      (let [config (-> {:database {:user "x" :password "?" :subname "stuff"}}
                       configure-dbs)]
        (is (= (get-in config [:read-database :maximum-pool-size]) 25))
        (is (= (get-in config [:database :maximum-pool-size]) 25))))

    (testing "migrate defaults to true"
      (let [config (-> {:database {:user "x" :password "?"
                                   :subname "stuff"}}
                       configure-dbs)]
        (is (= true (get-in config [:database :migrate])))))

    (testing "schema-check-interval defaults to 30 seconds"
      (let [config (-> {:database {:user "x" :password "?"
                                   :subname "stuff"}}
                       configure-dbs)
            thirty-seconds-in-millis 30000]
        (is (= (get-in config [:database :schema-check-interval]) thirty-seconds-in-millis))))

    (let [no-migrator {:database {:subname     "stuff"
                                  :username    "someone"
                                  :password    "something"}}
          migrator (update no-migrator :database assoc
                           :migrator-username "admin"
                           :migrator-password "admin")
          connection-user (update migrator :database assoc
                                  :connection-username "connection-user-value"
                                  :connection-migrator-username "connection-migration-user-value")
          ssl-connection {:database {:subname     "stuff?ssl=true"
                                     :username    "someone"}}]

      (testing "migrator-username"
        (let [config (configure-dbs no-migrator)]
          (is (= "someone" (get-in config [:database :migrator-username])))
          (is (= "someone" (get-in config [:read-database :migrator-username]))))
        (let [config (configure-dbs migrator)]
          (is (= "admin" (get-in config [:database :migrator-username])))
          (is (= "admin" (get-in config [:read-database :migrator-username])))))

      (testing "migrator-password"
        (let [config (configure-dbs no-migrator)]
          (is (= "something" (get-in config [:database :migrator-password])))
          (is (= "something" (get-in config [:read-database :migrator-password]))))
        (let [config (configure-dbs migrator)]
          (is (= "admin" (get-in config [:database :migrator-password])))
          (is (= "admin" (get-in config [:read-database :migrator-password])))))

      (testing "connection-usernames"
        (let [config (configure-dbs no-migrator)]
          (is (= "someone" (get-in config [:database :connection-username])))
          (is (= "someone" (get-in config [:database :connection-migrator-username]))))

        (let [config (configure-dbs migrator)]
          (is (= "someone" (get-in config [:database :connection-username])))
          (is (= "admin" (get-in config [:database :connection-migrator-username]))))

        (let [config (configure-dbs connection-user)]
          (is (= "connection-user-value" (get-in config [:database :connection-username])))
          (is (= "connection-migration-user-value" (get-in config [:database :connection-migrator-username])))))

      (testing "ssl-connection"
        (let [config (configure-dbs ssl-connection)]
          (is (= "" (get-in config [:database :migrator-password])))
          (is (= "" (get-in config [:read-database :migrator-password])))))

      (testing "ssl-connection-with-password"
        (let [config (configure-dbs (assoc-in ssl-connection [:database :password] "password"))]
          (is (= "password" (get-in config [:database :migrator-password])))
          (is (= "password" (get-in config [:read-database :migrator-password]))))))))

(deftest database-user-preferred-to-username-on-mismatch
  (let [config (configure-dbs {:database {:subname "stuff"
                                          :user "someone"
                                          :username "someone-else"
                                          :password "something"}})]
    (is (= "someone" (get-in config [:database :user])))
    (is (= "someone" (get-in config [:database :username])))))

(deftest multiple-database-configurations
  (let [config (conf/configure-dbs
                {:database {:username "u1" :password "?" :subname "s1"}
                 :database-primary {:password "?"}
                 :database-secondary {:username "u2" :password "?" :subname "s2"}})]
    ;; FIXME: read-database
    (is (= "u1" (get-in config [:database :user])))
    (is (= "u1" (get-in config [:database :username])))
    (is (= "u1" (get-in config [:database-primary :user])))
    (is (= "u1" (get-in config [:database-primary :username])))
    (is (= "u2" (get-in config [:database-secondary :user])))
    (is (= "u2" (get-in config [:database-secondary :username])))))

(deftest garbage-collection
  (let [config-with (fn [base-config]
                      (-> base-config
                          (update :database merge sample-db-config)
                          configure-dbs))]
    (testing "gc-interval"
      (testing "should use the value specified in minutes"
        (let [gc-interval (get-in (config-with {:database {:gc-interval "900"}})
                                  [:database :gc-interval])]
          (is (time/period? gc-interval))
          (is (= 900 (time/to-minutes gc-interval)))))
      (testing "should default to 60 minutes"
        (let [gc-interval (get-in (config-with {:database {}})
                                  [:database :gc-interval])]
          (is (time/period? gc-interval))
          (is (= 60 (time/to-minutes gc-interval)))))
      (testing "handles zero values"
        (let [gc-interval (get-in (config-with {:database {:gc-interval "0"}})
                                  [:database :gc-interval])]
          (is (time/period? gc-interval))
          (is (= 0 (time/to-minutes gc-interval)))))
      (testing "handles fractional values"
        (let [gc-interval (get-in (config-with {:database {:gc-interval "0.01"}})
                                  [:database :gc-interval])]
          (is (time/period? gc-interval))
          (is (= 600 (time/to-millis gc-interval)))))
      (testing "handles negative values"
        (is (thrown-with-msg? ExceptionInfo #"gc-interval cannot be negative"
                              (config-with {:database {:gc-interval "-1"}})))))

    (testing "gc-interval"
      (testing "should default to gc-interval"
        (let [config (config-with {:database {:gc-interval "900"}})]
          (doseq [c [:gc-interval-expire-nodes :gc-interval-purge-nodes :gc-interval-purge-reports
                     :gc-interval-packages :gc-interval-fact-paths :gc-interval-catalogs]]
            (let [gc-interval (get-in config [:database c])]
              (is (time/period? gc-interval))
              (if (= :gc-interval-fact-paths c)
                (is (= 1440 (time/to-minutes gc-interval)))
                (is (= 900 (time/to-minutes gc-interval))))))))

      (testing "should allow overriding individual configs"
        (let [config (config-with {:database {:gc-interval "900"
                                              :gc-interval-fact-paths "900"
                                              :gc-interval-purge-nodes "10"}})]
          (doseq [c [:gc-interval-expire-nodes :gc-interval-purge-nodes :gc-interval-purge-reports
                     :gc-interval-packages :gc-interval-fact-paths :gc-interval-catalogs]]
            (let [gc-interval (get-in config [:database c])]
              (is (time/period? gc-interval))
              (if (= c :gc-interval-purge-nodes)
                (is (= 10 (time/to-minutes gc-interval)))
                (is (= 900 (time/to-minutes gc-interval)))))))))

    (testing "node-ttl"
      (testing "should parse node-ttl and return a period"
        (let [node-ttl (get-in (config-with {:database {:node-ttl "10d"}})
                               [:database :node-ttl])]
          (is (time/period? node-ttl))
          (is (= (time/days 10) (time/days (time/to-days node-ttl))))))
      (testing "should default to 7 days"
        (let [node-ttl (get-in (config-with {}) [:database :node-ttl])]
          (is (time/period? node-ttl))
          (is (= 7 (time/to-days node-ttl))))))

    (testing "node-purge-ttl"
      (testing "should parse node-purge-ttl and return a period"
        (let [node-purge-ttl (get-in (config-with {:database {:node-purge-ttl "10d"}})
                               [:database :node-purge-ttl])]
          (is (time/period? node-purge-ttl))
          (is (= (time/days 10) (time/days (time/to-days node-purge-ttl))))))
      (testing "should default to 14 days"
        (let [node-purge-ttl (get-in (config-with {}) [:database :node-purge-ttl])]
          (is (time/period? node-purge-ttl))
          (is (= 14 (time/to-days node-purge-ttl))))))

    (testing "node-purge-gc-batch-limit"
      (testing "should use the value specified"
        (let [x (get-in (config-with {:database {:node-purge-gc-batch-limit 3}})
                        [:database :node-purge-gc-batch-limit])]
          (is (integer? x))
          (is (= 3 x))))
      (testing "should default to 25"
        (let [x (get-in (config-with {})
                        [:database :node-purge-gc-batch-limit])]
          (is (integer? x))
          (is (= 25 x)))))

    (testing "report-ttl"
      (testing "should parse report-ttl and produce report-ttl"
        (let [report-ttl (get-in (config-with {:database {:report-ttl "10d"}})
                                 [:database :report-ttl])]
          (is (time/period? report-ttl))
          (is (= (time/days 10) (time/days (time/to-days report-ttl))))))
      (testing "should default to 14 days"
        (let [report-ttl (get-in (config-with {}) [:database :report-ttl])]
          (is (time/period? report-ttl))
          (is (= (time/days 14) (time/days (time/to-days report-ttl)))))))

    (testing "resource-events-ttl"
      (testing "should parse resource-events-ttl and produce resource-events-ttl"
        (let [resource-events-ttl (get-in (config-with {:database {:resource-events-ttl "10d"}})
                                          [:database :resource-events-ttl])]
          (is (time/period? resource-events-ttl))
          (is (= (time/days 10) (time/days (time/to-days resource-events-ttl))))))
      (testing "should default to report-ttl"
        (let [resource-events-ttl (get-in (config-with {}) [:database :resource-events-ttl])]
          (is (time/period? resource-events-ttl))
          (is (= (time/days 14) (time/days (time/to-days resource-events-ttl)))))))))

(defn vardir [path]
  {:global {:vardir (str path)}})

(deftest vardir-validation
  (testing "should fail if it's not specified"
    (is (thrown-with-msg? IllegalArgumentException #"is not specified"
                          (validate-vardir {:global {:vardir nil}}))))

  (testing "should fail if it doesn't exist"
    (is (thrown-with-msg? java.io.FileNotFoundException #"does not exist"
                          (validate-vardir (vardir "/abc/def/ghi")))))

  (testing "should fail if it's not a directory"
    (let [filename (doto (java.io.File/createTempFile "not_a" "directory")
                     (.deleteOnExit))]
      (is (thrown-with-msg? java.io.FileNotFoundException #"is not a directory"
                            (validate-vardir (vardir filename))))))

  (testing "should fail if it's not writable"
    (let [filename (doto (java.io.File/createTempFile "not" "writable")
                     (.deleteOnExit)
                     (.delete)
                     (.mkdir)
                     (.setReadOnly))]
      (is (thrown-with-msg? java.io.FileNotFoundException #"is not writable"
                            (validate-vardir (vardir filename))))))

  (testing "should return the value if everything is okay"
    (let [filename (doto (java.io.File/createTempFile "totally" "okay")
                     (.deleteOnExit)
                     (.delete)
                     (.mkdir)
                     (.setWritable true))]
      (is (= (validate-vardir (vardir filename))
             (vardir filename)))))

  (testing "should support relative paths"
    (let [filename "./target/totally/okay"]
      (doto (fs/file filename)
        (.deleteOnExit)
        (.mkdirs)
        (.setWritable true))
      (is (= (validate-vardir (vardir filename))
             (vardir filename))))))

(deftest product-name-validation
  (doseq [product-name ["puppetdb" "pe-puppetdb"]]
    (testing (format "should accept %s and return it" product-name)
      (is (= product-name
             (normalize-product-name product-name)))))

  (doseq [product-name ["PUPPETDB" "PE-PUPPETDB" "PuppetDB" "PE-PuppetDB"]]
    (testing (format "should accept %s and return it lower-cased" product-name)
      (is (= (str/lower-case product-name)
             (normalize-product-name product-name)))))

  (testing "should disallow anything else"
    (is (thrown-with-msg? IllegalArgumentException #"product-name puppet is illegal"
                          (normalize-product-name "puppet")))))

(deftest warn-retirements-test
  (testing "output to standard out"
    (let [bad-config {:repl {:port 123}}
          out-str (with-out-str
                    (binding [*err* *out*]
                      (warn-retirements bad-config)))]
      (is (.contains out-str "[repl] is now retired"))))
  (testing "[database] config deprecations"
    (doseq [param [:classname :subprotocol]]
      (is (= (format "The [database] %s config option has been retired and will be ignored.\n"
                     (name param))
             (with-out-str
               (binding [*err* *out*]
                 (warn-retirements {:database {param "foo"}}))))))))

(deftest test-default-max-command-size
  (testing "default is disabled"
    (let [default-config (:command-processing (configure-command-processing {}))]
      (is (false? (:reject-large-commands default-config)))))

  (testing "enabled but with default size"
    (let [default-config (:command-processing (configure-command-processing {:command-processing {:reject-large-commands "true"}}))]
      (is (true? (:reject-large-commands default-config)))
      (is (= (default-max-command-size)
             (:max-command-size default-config)))))

  (testing "enabled with included max size"
    (let [default-config (:command-processing (configure-command-processing {:command-processing {:reject-large-commands "true"
                                                                                                  :max-command-size 25000}}))]
      (is (true? (:reject-large-commands default-config)))
      (is (= 25000
             (:max-command-size default-config))))))

(deftest query-timeout-settings
  (is (= 600 (-> (configure-puppetdb {}) (get-in [:puppetdb :query-timeout-default]))))
  (is (= ##Inf (-> (configure-puppetdb {}) (get-in [:puppetdb :query-timeout-max]))))
  (doseq [setting [:query-timeout-default :query-timeout-max]]
    (is (= ##Inf (-> (configure-puppetdb {:puppetdb {setting "0"}})
                     (get-in [:puppetdb setting]))))
    (is (= 10 (-> (configure-puppetdb {:puppetdb {setting "10"}})
                  (get-in [:puppetdb setting]))))
    (is (= 1.1 (-> (configure-puppetdb {:puppetdb {setting "1.1"}})
                   (get-in [:puppetdb setting]))))
    (let [ex (with-caught-ex-info (configure-puppetdb {:puppetdb {setting "-1"}}))]
      (is (= ::conf/cli-error (:type (ex-data ex))))
      (is (str/includes? (.getMessage ex) "must be non-negative number")))
    (let [ex (with-caught-ex-info (configure-puppetdb {:puppetdb {setting "x"}}))]
      (is (= ::conf/cli-error (:type (ex-data ex))))
      (is (str/includes? (.getMessage ex) "must be non-negative number")))))
