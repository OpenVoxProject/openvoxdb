(ns puppetlabs.puppetdb.cli.services-test
  (:require [clojure.set :refer [subset?]]
            [next.jdbc.plan :refer [select-one!]]
            [puppetlabs.http.client.sync :as pl-http]
            [puppetlabs.puppetdb.cli.util :refer [err-exit-status]]
            [puppetlabs.puppetdb.command.constants :as cmd-consts]
            [puppetlabs.puppetdb.lint :refer [ignore-value]]
            [puppetlabs.puppetdb.scf.partitioning
             :refer [create-resource-events-partition
                     create-reports-partition
                     get-temporal-partitions]
             :as part]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-logged-event-maps
                     with-log-level]]
            [puppetlabs.puppetdb.cli.services :as svcs
             :refer [collect-garbage
                     db-config->clean-request
                     init-with-db
                     init-write-dbs
                     query]]
            [puppetlabs.puppetdb.testutils.db
             :refer [*db* *read-db* with-test-db with-unconnected-test-db]]
            [puppetlabs.puppetdb.testutils.cli
             :refer [example-certname example-report get-factsets]]
            [puppetlabs.puppetdb.command :refer [enqueue-command]]
            [puppetlabs.puppetdb.config :as conf]
            [puppetlabs.puppetdb.jdbc :as jdbc]
            [puppetlabs.puppetdb.scf.storage :as scf-store]
            [puppetlabs.puppetdb.scf.storage-utils :as sutils]
            [puppetlabs.puppetdb.time :as time :refer [now to-string]]
            [puppetlabs.puppetdb.utils :as utils :refer [schedule]]
            [clojure.test :refer :all]
            [puppetlabs.puppetdb.testutils.services :as svc-utils
             :refer [*base-url*
                     *server*
                     call-with-puppetdb-instance
                     call-with-single-quiet-pdb-instance
                     create-temp-config
                     sync-command-post
                     with-pdb-with-no-gc]]
            [puppetlabs.trapperkeeper.app :as tkapp :refer [get-service]]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [puppetlabs.puppetdb.testutils :as tu
             :refer [block-until-results default-timeout-ms temp-file change-report-time]]
            [puppetlabs.puppetdb.testutils.queue :as tqueue]
            [clojure.string :as str])
  (:import
   (ch.qos.logback.classic Level)
   (ch.qos.logback.classic.spi ILoggingEvent)
   (clojure.lang ExceptionInfo)
   (java.util.concurrent CyclicBarrier)
   (java.util.concurrent.locks ReentrantLock)))

(defn- check-service-query
  [version q pagination check-result]
  (let [pdb-service (get-service svc-utils/*server* :PuppetDBServer)
        results (atom nil)
        before-slurp? (atom nil)
        after-slurp? (atom nil)]
    (query pdb-service version q pagination
           (fn [result-set]
             ;; We evaluate the first element from lazy-seq just to check if DB query was successful or not
             ;; so we have to ensure the first element and the rest have been realized, not just the first
             ;; element on its own.
             (reset! before-slurp? (and (realized? result-set) (realized? (rest result-set))))
             (reset! results (vec result-set))
             (reset! after-slurp? (and (realized? result-set) (realized? (rest result-set))))))
    (is (false? @before-slurp?))
    (check-result @results)
    (is (true? @after-slurp?))))

(deftest query-via-puppdbserver-service
  (svc-utils/with-single-quiet-pdb-instance
    (let [dispatcher (get-service svc-utils/*server* :PuppetDBCommandDispatcher)]
      (enqueue-command dispatcher
                       "replace facts"
                       4
                       "foo.local"
                       nil
                       (tqueue/coerce-to-stream
                        {:certname "foo.local"
                         :environment "DEV"
                         :values {:foo "the foo"
                                  :bar "the bar"
                                  :baz "the baz"}
                         :producer_timestamp (to-string (now))})
                       "")

      @(block-until-results 200 (first (get-factsets "foo.local")))

      (check-service-query
       :v4 ["from" "facts" ["=" "certname" "foo.local"]]
       nil
       (fn [result]
         (is (= #{{:value "the baz",
                   :name "baz",
                   :environment "DEV",
                   :certname "foo.local"}
                  {:value "the bar",
                   :name "bar",
                   :environment "DEV",
                   :certname "foo.local"}
                  {:value "the foo",
                   :name "foo",
                   :environment "DEV",
                   :certname "foo.local"}}
                (set result))))))))

(deftest query-with-explain
  (svc-utils/with-single-quiet-pdb-instance
      (check-service-query
       :v4 ["from" "facts" ["=" "certname" "foo.local"]]
       {:explain :analyze}
       (fn [result]
         (is (= true (contains? (first result) (keyword "query plan"))))
         (is (= true (instance? org.postgresql.util.PGobject ((keyword "query plan") (first result) ))))))))

(deftest pagination-via-puppdbserver-service
  (svc-utils/with-puppetdb-instance
    (let [dispatcher (get-service svc-utils/*server* :PuppetDBCommandDispatcher)]
      (enqueue-command dispatcher
                       "replace facts"
                       4
                       "foo.local"
                       nil
                       (tqueue/coerce-to-stream
                        {:certname "foo.local"
                         :environment "DEV"
                         :values {:a "a" :b "b" :c "c"}
                         :producer_timestamp (to-string (now))})
                       "")

      @(block-until-results 200 (first (get-factsets "foo.local")))
      (let [exp ["a" "b" "c"]
            rexp (reverse exp)]
        (doseq [order [:ascending :descending]
                offset (range (dec (count exp)))
                limit (range 1 (count exp))]
          (let [expected (take limit
                               (drop offset (if (= order :ascending) exp rexp)))]
            (check-service-query
             :v4 ["from" "facts" ["=" "certname" "foo.local"]]
             {:order_by [[:name order]]
              :offset offset
              :limit limit}
             (fn [result]
               (is (= (map #(hash-map :name % :value %
                                      :environment "DEV",
                                      :certname "foo.local")
                           expected)
                      result))))))))))

(deftest api-retirements
  (svc-utils/with-puppetdb-instance
    (letfn [(ping [v]
              (-> (svc-utils/pdb-query-url)
                  (assoc :version v)
                  (svc-utils/create-url-str "/facts")
                  svc-utils/get))
            (retirement-response? [v response]
              (and (= 404 (:status response))
                   (= (format "The %s API has been retired; please use v4"
                              (name v))
                      (:body  response))))]
      (is (= 200 (:status (ping :v4))))
      (doseq [v [:v1 :v2 :v3]]
        (testing (format "%s requests are refused" (name v)))
        (is (retirement-response? v (ping v)))))))

(defn make-https-request-with-allowlisted-host [allowlisted-host]
  (let [allowlist-file (temp-file "allowlist-log-reject")
        cert-config {:ssl-cert "test-resources/localhost.pem"
                     :ssl-key "test-resources/localhost.key"
                     :ssl-ca-cert "test-resources/ca.pem"}]
    (spit allowlist-file allowlisted-host)
    (with-test-db
      (svc-utils/call-with-puppetdb-instance
       (-> (svc-utils/create-temp-config)
           (assoc :database *db* :read-database *read-db*)
           (assoc :jetty (merge cert-config
                                {:ssl-port 0
                                 :ssl-host "0.0.0.0"
                                 :ssl-protocols "TLSv1,TLSv1.1,TLSv1.2"}))
           (assoc-in [:puppetdb :certificate-allowlist] (str allowlist-file)))
       (fn []
         (pl-http/get (str (utils/base-url->str (assoc *base-url* :version :v4))
                           "/facts")
                      (merge cert-config
                             {:headers {"accept" "application/json"}
                              :as :text})))))))

(deftest cert-allowlists
  (testing "hosts not in allowlist should be forbidden"
    (let [response (make-https-request-with-allowlisted-host "bogus")]
      (is (= 403 (:status response)))
      (is (re-find #"Permission denied" (:body response)))))

  (testing "host in the cert allowlist is allowed"
    (let [response (make-https-request-with-allowlisted-host "localhost")]
      (is (= 200 (:status response)))
      (is (not (re-find #"Permission denied" (:body response)))))))

(deftest unsupported-database-triggers-shutdown
  ;; Intercept and validate both the throw from start-puppetdb and the
  ;; subsequent shutdown request from start.
  (let [service (atom nil)
        start-ex (atom nil)
        orig-start svcs/start-puppetdb
        start (fn [& args]
                (let [[_ _ svc _ _ _] args]
                  (reset! service svc)
                  (try
                    (let [result (apply orig-start args)]
                      (reset! start-ex nil)
                      result)
                    (catch Exception ex
                      (reset! start-ex ex)
                      (throw ex)))))
        err-msg? #(re-matches #"PostgreSQL 9\.5 is no longer supported\. .*" %1)]

    ;; err-msg wrt log suppression?

    (with-redefs [sutils/db-metadata (fn [] {:database nil :version [9 5]})
                  svcs/start-puppetdb start]
      (svc-utils/with-puppetdb-instance
        true))

    (testing "unsupported db triggers unsupported-database exception"
      (let [ex (deref start-ex)
            expected-oldest scf-store/oldest-supported-db
            {:keys [kind current oldest]} (when (instance? ExceptionInfo ex)
                                            (ex-data ex))]
        (is (= ExceptionInfo (class ex)))
        (is (= ::svcs/unsupported-database kind))
        (is (= [9 5] current))
        (is (= expected-oldest oldest))))

    (testing "unsupported-database exception causes shutdown request"
      (let [opts (-> @service service-context :shutdown-request deref :opts)
            exit (:puppetlabs.trapperkeeper.core/exit opts)]
        (is (= err-exit-status (:status exit)))
        (is (some (fn [[msg _out]] (err-msg? msg))
                  (:messages exit)))))))

(deftest unsupported-database-settings-trigger-shutdown
  (let [bad-setting :standard_conforming_strings
        bad-value "off"
        service (atom nil)
        start-ex (atom nil)
        orig-req svcs/request-database-settings
        request-settings #(for [{n :name :as settings} (orig-req)]
                            (if (= n (name bad-setting))
                              (assoc settings :setting bad-value)
                              settings))
        orig-start svcs/start-puppetdb
        start (fn [& args]
                (let [[_ _ svc _ _] args]
                  (reset! service svc)
                  (try
                    (let [result (apply orig-start args)]
                      (reset! start-ex nil)
                      result)
                    (catch Exception ex
                      (reset! start-ex ex)
                      (throw ex)))))
        err-msg? #(re-matches #"Invalid database configuration settings: 'standard_.*" %1)]

    ;; err-msg wrt log suppression?

    (with-redefs [svcs/request-database-settings request-settings
                  svcs/start-puppetdb start]
      (svc-utils/with-puppetdb-instance
        true))

    (testing "invalid-database-configuration exception thrown"
      (let [ex (deref start-ex)
            {:keys [kind failed-validation]} (when (instance? ExceptionInfo ex)
                                               (ex-data ex))]
        (is (= ExceptionInfo (class ex)))
        (is (= ::svcs/invalid-database-configuration kind))
        (is (= (get-in failed-validation [bad-setting :actual]) bad-value))))

    (testing "invalid-database-configuration exception causes shutdown request"
      (let [opts (-> @service service-context :shutdown-request deref :opts)
            exit (:puppetlabs.trapperkeeper.core/exit opts)]
        (is (= err-exit-status (:status exit)))
        (is (some (fn [[msg _out]] (err-msg? msg))
                  (:messages exit)))))))

(defn purgeable-nodes [node-purge-ttl]
  (let [horizon (time/to-timestamp (time/ago node-purge-ttl))]
    (jdbc/query-to-vec
     "select * from certnames_status where deactivated < ? or expired < ?"
     horizon horizon)))

(deftest node-purge-gc-batch-limit
  (doseq [[limit expected-remaining] [[0 0] [7 3] [100 0]]]
    (with-pdb-with-no-gc
      (let [config (-> *server* (get-service :DefaultedConfig) conf/get-config)
            node-purge-ttl (get-in config [:database :node-purge-ttl])
            deactivation-time (time/to-timestamp (time/ago node-purge-ttl))
            lock (ReentrantLock.)]
        (dotimes [i 10]
          (let [name (str "foo-" i)]
            (scf-store/add-certname! name)
            (scf-store/deactivate-node! name deactivation-time)))
        (let [cfg (-> *server* (get-service :DefaultedConfig) conf/get-config)
              db-cfg (assoc (:database cfg) :node-purge-gc-batch-limit limit)
              db-lock-status (svcs/database-lock-status)]
          (collect-garbage db-cfg lock db-cfg db-lock-status
                           (mapcat first (db-config->clean-request db-cfg))))
        (is (= expected-remaining
               (count (purgeable-nodes node-purge-ttl))))))))

(deftest test-stop-with-blocked-scheduler
  (let [requested-shutdown? (promise)
        ready-to-go? (promise)
        notable? (fn notable-blocked-scheduler-event?
                   [^ILoggingEvent event]
                   (and (.isGreaterOrEqual (.getLevel event) Level/ERROR)
                        (let [m (.getMessage event)]
                          (not (or (str/includes? m "Unable to shut down job scheduler")
                                   (str/includes? m "Unable to read schema version"))))))]
    (with-redefs [svcs/stop-gc-wait-ms (constantly 10)
                  svcs/shut-down-after-scheduler-unresponsive
                  (fn [f]
                    (deliver requested-shutdown? true)
                    (f))]
      (binding [svc-utils/*notable-log-event?* notable?]
        (with-pdb-with-no-gc
          (let [pdb (get-service *server* :PuppetDBServer)
                pool (-> pdb service-context :job-pool)]
            (schedule pool
                      #(do
                         (deliver ready-to-go? true)
                         (while (not (try
                                       @requested-shutdown?
                                       (catch InterruptedException _
                                         false)))
                           (ignore-value true)))
                      0)
            (is (= true (deref ready-to-go? default-timeout-ms false)))
            (tkapp/stop *server*)
            (is (= true @requested-shutdown?))))))))

(deftest regular-gc-drops-oldest-partitions-incrementally
  (with-unconnected-test-db
    (let [config (-> (create-temp-config)
                     (assoc :database *db* :read-database *read-db*)
                     (assoc-in [:database :gc-interval] "0.01"))
          before-gc (CyclicBarrier. 2)
          after-gc (CyclicBarrier. 2)
          original-periodic-gc svcs/invoke-periodic-gc
          invoke-periodic (fn [& args]
                            (if (some #{"purge_reports"} (nth args 2))
                              (do
                                (.await before-gc)
                                (let [result (apply original-periodic-gc args)]
                                  (.await after-gc)
                                  result))
                              (apply original-periodic-gc args)))]
      (with-redefs [svcs/invoke-periodic-gc invoke-periodic]
        (call-with-single-quiet-pdb-instance
         config
         (fn []
           ;; Wait for the first, full gc to finish.
           (.await before-gc)
           (.await after-gc)
           (let [date1 (time/wire-datetime->instant "2011-01-01T12:00:01-03:00")
                 date2 (time/wire-datetime->instant "2011-01-02T12:00:01-03:00")]
             ;; Create partitions manually, old events partitions are filtered out in storage
             (create-reports-partition date1)
             (create-resource-events-partition date1)

             (create-reports-partition date2)
             (create-resource-events-partition date2))

           (let [report-parts (set (get-temporal-partitions "reports_historical"))
                 event-parts (set (get-temporal-partitions "resource_events_historical"))]

             (is (subset? #{{:table "reports_20110101z", :part "20110101z"}
                            {:table "reports_20110102z", :part "20110102z"}}
                          report-parts))

             (is (subset? #{{:table "resource_events_20110101z", :part "20110101z"}
                            {:table "resource_events_20110102z", :part "20110102z"}}
                          event-parts))

             ;; Let the gc go and make sure it only drops the oldest partition
             (.await before-gc)
             (.await after-gc)
             (is (= (->> report-parts (sort-by :table) (drop 1) set)
                    (set (get-temporal-partitions "reports_historical"))))
             (is (= (->> event-parts (sort-by :table) (drop 1) set)
                    (set (get-temporal-partitions "resource_events_historical"))))

             ;; Let the gc go and make sure it only drops the oldest partition
             (.await before-gc)
             (.await after-gc)
             (is (= (->> report-parts (sort-by :table) (drop 2) set)
                    (set (get-temporal-partitions "reports_historical"))))
             (is (= (->> event-parts (sort-by :table) (drop 2) set)
                    (set (get-temporal-partitions "resource_events_historical")))))))))))

(deftest partition-gc-clears-queries-blocking-it-from-getting-accessexclusivelocks
  (with-unconnected-test-db
    (let [config (-> (create-temp-config)
                     (assoc :database *db* :read-database *read-db*)
                     (assoc-in [:database :gc-interval] "0.01"))
          store-report #(sync-command-post (svc-utils/pdb-cmd-url)
                                           example-certname
                                           "store report"
                                           cmd-consts/latest-report-version
                                           (change-report-time example-report %))
          after-gc (CyclicBarrier. 2)
          before-gc (CyclicBarrier. 2)
          queries-begun (CyclicBarrier. 4)
          original-periodic-gc svcs/invoke-periodic-gc
          invoke-periodic (fn [& args]
                            (if (some #{"purge_reports"} (nth args 2))
                              (do
                                (.await before-gc)
                                (let [result (apply original-periodic-gc args)]
                                  (.await after-gc)
                                  result))
                              ;; if not purging reports, run gc normally
                              (apply original-periodic-gc args)))
          event-expired? (fn [_ _] true)]
      (with-redefs [svcs/invoke-periodic-gc invoke-periodic
                    scf-store/resource-event-expired? event-expired?]
        (call-with-puppetdb-instance
         config
         (fn []
           (with-log-level "puppetlabs.puppetdb.scf.storage" :info
             (with-logged-event-maps log
               ;; Wait for the first, full gc to finish.
               (.await before-gc)
               (.await after-gc)
               (store-report "2011-01-01T12:00:01-03:00")
               (store-report "2011-01-02T12:00:01-03:00")
               ;; store a latest report to move the other two into historical partitions
               (store-report (to-string (now)))

               (let [report-parts (set (get-temporal-partitions "reports_historical"))
                     event-parts (set (get-temporal-partitions "resource_events_historical"))]

                 (is (subset? #{{:table "reports_20110101z", :part "20110101z"}
                                {:table "reports_20110102z", :part "20110102z"}}
                              report-parts))

                 (is (subset? #{{:table "resource_events_20110101z", :part "20110101z"}
                                {:table "resource_events_20110102z", :part "20110102z"}}
                              event-parts))

                 ;; these queries will sleep in front of the next GC preventing it from getting
                 ;; the AccessExclusiveLock it needs, both should get canceled by GC
                 (let [report-query (future
                                      (jdbc/with-transacted-connection *read-db*
                                        (.await queries-begun)
                                        (jdbc/do-commands "select id, pg_sleep(12) from reports")))
                       report-query2 (future
                                       (jdbc/with-transacted-connection *db*
                                         (.await queries-begun)
                                         (jdbc/do-commands "select id, pg_sleep(12) from reports")))
                       resource-query (future
                                        (jdbc/with-transacted-connection *db*
                                          (.await queries-begun)
                                          (jdbc/do-commands "select event_hash, pg_sleep(12) from resource_events")))]

                   ;; Intention is to ensure that the queries have begun before gc starts and the query bulldozer whizzes by,
                   ;; because if the bulldozer zips through before the queries are actually made, nothing gets cleared.
                   (.await queries-begun)
                   (Thread/sleep 1000)

                   ;; gc should clear the three queries from above and drop only the oldest partition
                   (.await before-gc)
                   (.await after-gc)

                   (is (thrown-with-msg?
                        java.util.concurrent.ExecutionException
                        #"FATAL: terminating connection due to administrator command"
                        (deref report-query default-timeout-ms :timeout-getting-expected-query-ex)))
                   (is (thrown-with-msg?
                        java.util.concurrent.ExecutionException
                        #"FATAL: terminating connection due to administrator command"
                        (deref report-query2 default-timeout-ms :timeout-getting-expected-query-ex)))
                   (is (thrown-with-msg?
                        java.util.concurrent.ExecutionException
                        #"FATAL: terminating connection due to administrator command"
                        (deref resource-query default-timeout-ms :timeout-getting-expected-query-ex))))

                 ;; There should be at least two cancelled messages in
                 ;; the logs. One from the queries cancelled by report
                 ;; GC and one from resource_events GC.  There may be
                 ;; more if any of the SIGTERMed pg workers don't exit
                 ;; before the bulldozer runs again.
                 (is (<= 2 (->> @log
                                (map :message)
                                (map #(str/includes? % "Partition GC terminated queries"))
                                (filter true?)
                                count)))

                 (is (= (->> report-parts (sort-by :table) (drop 1) set)
                        (set (get-temporal-partitions "reports_historical"))))
                 (is (= (->> event-parts (sort-by :table) (drop 1) set)
                        (set (get-temporal-partitions "resource_events_historical"))))

                 (.await before-gc)
                 (.await after-gc)

                 (is (= (->> report-parts (sort-by :table) (drop 2) set)
                        (set (get-temporal-partitions "reports_historical"))))
                 (is (= (->> event-parts (sort-by :table) (drop 2) set)
                        (set (get-temporal-partitions "resource_events_historical")))))))))))))

(deftest initialize-db
  (testing "when establishing migration database connections"
    (let [con-mig-user "conn-migration-user-value"
          mig-pass     "migrator-password-value"
          user         "user-value"
          pass         "password-value"
          validation-fn (fn [config _metrics-registry]
                          (is (= con-mig-user (:connection-migrator-username config)))
                          (is (= con-mig-user (:user config)))
                          (is (= mig-pass (:migrator-password config)))
                          (is (= mig-pass (:password config)))
                          ; We throw exception in order to interrupt execution in `init-with-db` function.
                          (throw (Exception.
                                   "everything ok exception")))]
      (with-redefs [jdbc/make-connection-pool validation-fn]
        (testing "should use connection migrator user"
          (is (thrown-with-msg?
                Exception #"everything ok exception"
                (init-with-db "test-db"
                              {:connection-migrator-username con-mig-user
                               :migrator-password            mig-pass
                               :user                         user
                               :password                     pass}
                              "ignored"))))))))

(deftest initialize-write-dbs
  (testing "when establishing write database connections"
    (let [connection-username "conn-user-value"
          databases {"default-database" , {:connection-username connection-username}}
          validation-fn (fn [options _metrics-registry]
                          (is (= connection-username (:user options))))]
      (with-redefs [jdbc/pooled-datasource validation-fn]
        (testing "should use connection user"
          (init-write-dbs databases))))))

(deftest correctly-sweep-reports
  (with-test-db
    (let [config (-> (create-temp-config)
                     (assoc :database *db* :read-database *read-db*)
                     (assoc-in [:database :gc-interval] "60"))
          store-report #(sync-command-post (svc-utils/pdb-cmd-url)
                                           example-certname
                                           "store report"
                                           cmd-consts/latest-report-version
                                           (change-report-time example-report %))
          db-lock-status (svcs/database-lock-status)]
      (call-with-puppetdb-instance
       config
       (fn []
         (testing "A report from 24 hours ago won't be gc'ed with a report-ttl of 1d"
           (store-report (-> 1 time/days time/ago time/to-string))
           (store-report (to-string (now)))
           (svcs/sweep-reports! *db* {:incremental? false
                                      :report-ttl (time/parse-period "1d")
                                      :resource-events-ttl (time/parse-period "1d")
                                      :db-lock-status db-lock-status})
           (is (= 1 (count (jdbc/query ["SELECT * FROM reports_historical"]))))
           (is (= 1 (count (jdbc/query ["SELECT * FROM reports_latest"]))))
           (jdbc/do-commands "DELETE FROM reports"))
         (testing "A report from 48 hours ago will be gc'ed with a report-ttl of 1d"
           (store-report (-> 2 time/days time/ago time/to-string))
           (store-report (to-string (now)))
           (svcs/sweep-reports! *db* {:incremental? false
                                      :report-ttl (time/parse-period "1d")
                                      :resource-events-ttl (time/parse-period "1d")
                                      :db-lock-status db-lock-status})
           (is (= 1 (count (jdbc/query ["SELECT * FROM reports_latest"]))))
           (is (empty? (jdbc/query ["SELECT * FROM reports_historical"])))
           (jdbc/do-commands "DELETE FROM reports"))

         ;; These tests are not applicable unless our Postgres version is new enough
         ;; to support the concurrent partition detach feature.
         (when (scf-store/detach-partitions-concurrently?)
           (testing "a partition stuck in the pending state is finalized and removed"
             (let [old-ts (-> 2 time/days time/ago)
                   partition-table (format "reports_%s"
                                           (part/date-suffix (part/to-zoned-date-time (time/to-timestamp old-ts))))
                   lock-acquired (promise)
                   partition-pending-detach (promise)]
               (store-report (time/to-string old-ts))
               (store-report (to-string (now)))

               (future
                ;; Create a query that will block the ACCESS EXCLUSIVE lock needed
                ;; by the second transaction of the concurrent detach below
                (jdbc/with-transacted-connection *read-db*
                  (jdbc/with-db-transaction  []
                    (jdbc/query [(format "select * from %s" partition-table)])
                    (deliver lock-acquired partition-table)

                    ;; wait for partition detach to fail
                    @partition-pending-detach)))

               ;; Wait until we are sure that the detach partition operation will be blocked
               @lock-acquired

               (try
                 (jdbc/do-commands-outside-txn
                  "SET statement_timeout = 100"
                  (format "ALTER TABLE reports_historical DETACH PARTITION %s CONCURRENTLY" partition-table))
                 (catch java.sql.SQLException _)
                 (finally
                   (deliver partition-pending-detach partition-table)
                   (jdbc/do-commands-outside-txn "SET statement_timeout = 0")))

               (is (= [{:inhdetachpending true}]
                      (jdbc/query ["select inhdetachpending from pg_catalog.pg_inherits where inhparent = 'reports_historical'::regclass and inhrelid = ?::regclass" partition-table])))

               (svcs/sweep-reports! *db* {:incremental? false
                                          :report-ttl (time/parse-period "1d")
                                          :resource-events-ttl (time/parse-period "1d")
                                          :db-lock-status db-lock-status})

               (is (empty?
                    (jdbc/query ["SELECT tablename FROM pg_tables WHERE tablename = ?" partition-table])))

               (jdbc/do-commands "DELETE FROM reports")))

           (testing "a detached partition that was not removed is cleaned up by gc"
             (let [old-ts (-> 2 time/days time/ago)
                   partition-table (format "reports_%s"
                                           (part/date-suffix (part/to-zoned-date-time (time/to-timestamp old-ts))))]
               (store-report (time/to-string old-ts))
               (store-report (to-string (now)))

               ;; Strand the partition before calling GC
               (jdbc/do-commands-outside-txn
                (format "ALTER TABLE reports_historical DETACH PARTITION %s CONCURRENTLY" partition-table))

               (svcs/sweep-reports! *db* {:incremental? false
                                          :report-ttl (time/parse-period "1d")
                                          :resource-events-ttl (time/parse-period "1d")
                                          :db-lock-status db-lock-status})

               (is (empty?
                    (jdbc/query ["SELECT tablename FROM pg_tables WHERE tablename = ?" partition-table])))

               (jdbc/do-commands "DELETE FROM reports")))))))))

(deftest reports-analysis
  ;; For now, just test for the initial invocation
  (let [start (now)
        is-analyzed
        (fn is-analyzed
          ([table] (is-analyzed table 0))
          ([table i]
           (let [r (jdbc/with-db-transaction []
                     (->> [(str "select last_analyze, last_autoanalyze"
                                "  from pg_stat_user_tables where relname = '" table "'")]
                          (select-one! (jdbc/connection) [:last_analyze :last_autoanalyze])))
                 last-ms (some-> r :last_analyze .getTime time/from-long)]
             (if (and last-ms (nil? (:last_autoanalyze r)))
               (do
                 (is (= nil (:last_autoanalyze r)))
                 (is (time/after? last-ms start)))
               (if (= i 100)
                 (is false (str table " was eventually analyzed"))
                 (do
                   (Thread/sleep 100)
                   (is-analyzed table (inc i))))))))]
    (svc-utils/with-puppetdb-instance
      (doseq [parent ["reports" "reports_historical" "reports_latest"
                      "resource_events" "resource_events_historical" "resource_events_latest"]]
        (testing (str parent " analysis times")
          (is-analyzed parent))))))
