(def pdb-version "8.11.1-SNAPSHOT")

(def clj-parent-version "7.4.0")

(defn true-in-env? [x]
  (#{"true" "yes" "1"} (System/getenv x)))

(defn pdb-run-sh [& args]
  (apply vector
         ["run" "-m" "puppetlabs.puppetdb.dev.lein/run-sh" (pr-str args)]))

(defn pdb-run-clean [paths]
  (apply pdb-run-sh {:argc #{0} :echo true} "rm" "-rf" paths))

(def pdb-jvm-ver
  ;; https://docs.oracle.com/javase/10/docs/api/java/lang/Runtime.Version.html
  ;; ...and do something similar for older versions.
  (let [prop (System/getProperty "java.version")]
    ;; Remove java.version use when JDK < 9 is no longer supported.
    (if (clojure.string/starts-with? prop "1.")
      ;; Versions prior to 10 were numbered 1.X, e.g. 1.8
      (let [[_ feature interim] (clojure.string/split prop #"[._]")]
        {:feature (Long/valueOf feature) :interim (Long/valueOf interim)})
      ;; Eval so we don't break compilation on older JDKs
      (let [ver (eval '(java.lang.Runtime/version))]
        {:feature (.feature ver) :interim (.interim ver)}))))

(def i18n-version "0.9.2")

(def pdb-dev-deps
  (concat
   '[[ring/ring-mock]
     [timofreiberg/bultitude "0.3.1"]
     [puppetlabs/trapperkeeper :classifier "test"]
     [puppetlabs/kitchensink :classifier "test"]
     [com.puppetlabs/trapperkeeper-webserver-jetty10 :classifier "test"]
     [org.flatland/ordered "1.15.12"]
     [org.clojure/test.check "1.1.1"]
     [com.gfredericks/test.chuck "0.2.14"]
     [riddley "0.2.0"]
     [clj-commons/clj-yaml "1.0.27"]
     [org.yaml/snakeyaml]

     ;; Only needed for :integration tests
     [puppetlabs/trapperkeeper-filesystem-watcher nil]]))

;; Don't use lein :clean-targets so that we don't have to repeat
;; ourselves, given that we need to remove some protected files, and
;; in addition, metadata like {:protect false} doesn't appear to
;; survive profile merges.

(def pdb-clean-paths
  ["puppet/client_data"
   "puppet/client_yaml"
   "puppet/clientbucket"
   "puppet/facts.d"
   "puppet/locales"
   "puppet/preview"
   "puppet/state"
   "resources/locales.clj"
   "resources/puppetlabs/puppetdb/Messages_eo$1.class"
   "resources/puppetlabs/puppetdb/Messages_eo.class"
   "target"
   "target-gems"
   "test-resources/puppetserver/ssl/certificate_requests"
   "test-resources/puppetserver/ssl/private"])

(def pdb-distclean-paths
  (into pdb-clean-paths
        [".bundle"
         ".lein-failures"
         "Gemfile.lock"
         "ext/test-conf/pgbin-requested"
         "ext/test-conf/pgport-requested"
         "ext/test-conf/puppet-ref-requested"
         "ext/test-conf/puppetserver-dep"
         "ext/test-conf/puppetserver-ref-requested"
         "puppetserver"
         "vendor"]))

(def pdb-aot-namespaces
  (apply vector
         #"puppetlabs\.puppetdb\..*"
         (->> "resources/puppetlabs/puppetdb/bootstrap.cfg"
              clojure.java.io/reader
              line-seq
              (map clojure.string/trim)
              (remove #(re-matches #"#.*" %))  ;; # comments
              (remove #(re-matches #"puppetlabs\.puppetdb\.." %))
              (map #(clojure.string/replace % #"(.*)/[^/]+$" "$1"))
              (map symbol))))

;; Avoid startup reflection warnings due to
;; https://clojure.atlassian.net/browse/CLJ-2066
;; https://openjdk.java.net/jeps/396
(def pdb-jvm-opts (when (< 8 (:feature pdb-jvm-ver) 17)
                    ["--illegal-access=deny"]))

(defproject puppetlabs/puppetdb pdb-version
  :description "OpenVox-integrated catalog and fact storage"

  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :url "https://docs.puppetlabs.com/puppetdb/"

  :min-lein-version "2.7.1"

  :parent-project {:coords [org.openvoxproject/clj-parent ~clj-parent-version]
                   :inherit [:managed-dependencies]}

  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort

  :dependencies [[org.postgresql/postgresql]
                 [org.clojure/clojure]
                 [org.clojure/core.async]
                 [org.clojure/core.match "0.3.0"]
                 [org.clojure/core.memoize]
                 [org.clojure/data.generators "1.1.0"]
                 [org.clojure/java.jdbc]
                 [org.clojure/tools.macro]
                 [org.clojure/tools.namespace]
                 [org.clojure/math.combinatorics "0.1.1"]
                 [org.clojure/tools.logging]
                 [org.clojure/tools.nrepl]

                 ;; Puppet specific
                 [puppetlabs/comidi]
                 [puppetlabs/i18n]
                 [puppetlabs/kitchensink]
                 [puppetlabs/ssl-utils]
                 [puppetlabs/stockpile "0.0.4"]
                 [puppetlabs/structured-logging]
                 [puppetlabs/trapperkeeper]
                 [com.puppetlabs/trapperkeeper-webserver-jetty10]
                 [puppetlabs/trapperkeeper-metrics]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/trapperkeeper-authorization]

                 ;; Various
                 [cheshire]
                 [clj-stacktrace]
                 [clj-time]
                 [com.rpl/specter "1.1.4"]
                 [com.github.seancorfield/next.jdbc "1.3.939"]
                 [com.taoensso/nippy :exclusions [org.tukaani/xz]]
                 [digest "1.4.10"]
                 [fast-zip "0.4.0"]
                 [instaparse]
                 [murphy "0.5.2"]
                 [clj-commons/fs]
                 [metrics-clojure]
                 [robert/hooke "1.3.0"]
                 [trptcolin/versioneer]
                 ;; We do not currently use this dependency directly, but
                 ;; we have documentation that shows how users can use it to
                 ;; send their logs to logstash, so we include it in the jar.
                 [net.logstash.logback/logstash-logback-encoder]
                 [com.fasterxml.jackson.core/jackson-databind]

                 ;; Filesystem utilities
                 [org.apache.commons/commons-lang3]

                 ;; Database connectivity
                 [com.zaxxer/HikariCP]
                 [com.github.seancorfield/honeysql]

                 ;; WebAPI support libraries.
                 [bidi]
                 [clj-http "3.13.1"]
                 [commons-io]
                 [compojure]
                 [ring/ring-core]]

  :jvm-opts ~pdb-jvm-opts

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                     :username :env/CLOJARS_USERNAME
                                     :password :env/CLOJARS_PASSWORD
                                     :sign-releases false}]]

  :plugins [[lein-release "1.1.3" :exclusions [org.clojure/clojure]]
            [lein-cloverage "1.2.4"]
            [lein-parent "0.3.9"]
            [puppetlabs/i18n ~i18n-version]]

  :lein-release {:scm        :git
                 :deploy-via :lein-deploy}

  :uberjar-name "puppetdb.jar"

  ;; WARNING: these configuration options are
  ;; also set in PE's project.clj changes to things like
  ;; the main namespace and start timeout need to be updated
  ;; there as well
  :lein-ezbake {:vars {:user "puppetdb"
                       :group "puppetdb"
                       :build-type "foss"
                       :package-name "openvoxdb"
                       :puppet-platform-version 8
                       :main-namespace "puppetlabs.puppetdb.cli.services"
                       :start-timeout 14400
                       :repo-target "openvox8"
                       :nonfinal-repo-target "openvox8-nightly"
                       :logrotate-enabled false
                       :replaces-pkgs [{:package "puppetdb" :version ""}]
                       :java-args ~(str "-Xmx192m "
                                        "-Djdk.tls.ephemeralDHKeySize=2048")}
                :config-dir "ext/config/foss"}

  ;; Build a puppetdb-VER-test.jar containing test/ for projects like
  ;; pdbext to use by depending on ["puppetlabs.puppetdb" :classifier
  ;; "test"].  See the :testutils profile below.
  :classifiers  {:test :testutils}

  :profiles {:defaults {:resource-paths ["test-resources"]
                        :dependencies ~pdb-dev-deps
                        :injections [(do
                                       (require 'schema.core)
                                       (schema.core/set-fn-validation! true))]}
             :dev [:defaults {:dependencies [[org.bouncycastle/bcpkix-jdk18on]]
                              :plugins [[jonase/eastwood "1.4.3"]]
                              :jvm-opts ~(conj pdb-jvm-opts "-XX:-OmitStackTraceInFastThrow")}]

             :fips [:defaults
                    {:dependencies [[org.bouncycastle/bcpkix-fips]
                                    [org.bouncycastle/bc-fips]
                                    [org.bouncycastle/bctls-fips]]

                     ;; this only ensures that we run with the proper profiles
                     ;; during testing. This JVM opt will be set in the puppet module
                     ;; that sets up the JVM classpaths during installation.
                     :jvm-opts ~(let [{:keys [feature interim]} pdb-jvm-ver]
                                  (conj pdb-jvm-opts
                                        (case feature
                                          8 "-Djava.security.properties==dev-resources/jdk8-fips-security"
                                          11 "-Djava.security.properties==dev-resources/jdk11on-fips-security"
                                          17 "-Djava.security.properties==dev-resources/jdk11on-fips-security"
                                          (do)
                                        )))}]
             :kondo {:dependencies [[clj-kondo "2024.05.24"]]}
             :ezbake {:dependencies ^:replace [;; NOTE: we need to explicitly pass in `nil` values
                                               ;; for the version numbers here in order to correctly
                                               ;; inherit the versions from our parent project.
                                               ;; This is because of a bug in lein 2.7.1 that
                                               ;; prevents the deps from being processed properly
                                               ;; with `:managed-dependencies` when you specify
                                               ;; dependencies in a profile.  See:
                                               ;; https://github.com/technomancy/leiningen/issues/2216
                                               ;; Hopefully we can remove those `nil`s (if we care)
                                               ;; and this comment when lein 2.7.2 is available.

                                               ;; ezbake does not use the uberjar profile so we need
                                               ;; to duplicate this dependency here
                                               [org.bouncycastle/bcpkix-jdk18on nil]

                                               ;; we need to explicitly pull in our parent project's
                                               ;; clojure version here, because without it, lein
                                               ;; brings in its own version, and older versions of
                                               ;; lein depend on clojure 1.6.
                                               [org.clojure/clojure nil]

                                               ;; This circular dependency is required because of a bug in
                                               ;; ezbake (EZ-35); without it, bootstrap.cfg will not be included
                                               ;; in the final package.
                                               [puppetlabs/puppetdb ~pdb-version]]
                      :name "puppetdb"
                      :plugins [[puppetlabs/lein-ezbake ~(or (System/getenv "EZBAKE_VERSION") "3.0.1-SNAPSHOT")]]}
             :testutils {:source-paths ^:replace ["test"]
                         :resource-paths ^:replace []
                         ;; Something else may need adjustment, but
                         ;; without this, "lein uberjar" tries to
                         ;; compile test files, and crashes because
                         ;; "src" namespaces aren't available.
                         :aot ^:replace []}
             :ci {:plugins [[lein-pprint "1.3.2"]
                            [lein-exec "0.3.7"]]}
             ; We only want to include bouncycastle in the FOSS uberjar.
             ; PE should be handled by selecting the proper bouncycastle jar
             ; at runtime (standard/fips)
             :uberjar {:dependencies [[org.bouncycastle/bcpkix-jdk18on]]
                       :aot ~pdb-aot-namespaces}}

  :jar-exclusions [#"leiningen/"]

  :resource-paths ["resources" "puppet/lib" "resources/puppetlabs/puppetdb" "resources/ext/docs"]

  :main puppetlabs.puppetdb.core

  :test-selectors {:default (fn [m] (not (or (:integration m) (:skipped m))))
                   :unit (fn [m] (not (or (:integration m) (:skipped m))))
                   :integration :integration
                   :skipped :skipped}

  ;; This is used to merge the locales.clj of all the dependencies into a single
  ;; file inside the uberjar
  :uberjar-merge-with {"locales.clj"  [(comp read-string slurp)
                                       (fn [new prev]
                                         (if (map? prev) [new prev] (conj prev new)))
                                       #(spit %1 (pr-str %2))]}


  :eastwood {:config-files ["eastwood.clj"]
             ;; local-shadows-var is too distruptive, particularly
             ;; with respect to defservice dependency methods, and
             ;; since there's no facility for more targeted overrides
             ;; yet, disable it for now.
             :exclude-linters [:local-shadows-var :reflection]}

  :aliases {"kondo" ["with-profile" "+kondo" "run" "-m" "clj-kondo.main"]
            "clean" ~(pdb-run-clean pdb-clean-paths)
            "distclean" ~(pdb-run-clean pdb-distclean-paths)
            "test-in-parallel" ["run"
                                "-m" "puppetlabs.puppetdb.test-in-parallel/main"
                                "--test-paths" :project/test-paths]
            "time-shift-export" ^{:doc (clojure.string/join "" ["Shifts all timestamps from a PuppetDB archive with"
                                        " the period between the most recent one in the archive and the one "
                                        "you provide, or the current date."])}
                               ["trampoline" "run" "-m" "puppetlabs.puppetdb.cli.time-shift-export"]
            "pdb-dataset" ^{:doc (clojure.string/join "" ["Restores an empty database from a pg_dump resulted backup"
                                  " file and shifts all timestamps with the period between the most recent one in"
                                  " the databse and the one you provide, or the current date."])}
                          ["trampoline" "run" "-m" "puppetlabs.puppetdb.cli.pdb-dataset"]})
