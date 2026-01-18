(def pdb-version "8.12.0-SNAPSHOT")

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

(def pdb-dev-deps
  (concat
   '[[ring/ring-mock]
     [timofreiberg/bultitude]
     [org.openvoxproject/trapperkeeper :classifier "test"]
     [org.openvoxproject/kitchensink :classifier "test"]
     [org.openvoxproject/trapperkeeper-webserver-jetty10 :classifier "test"]
     [org.flatland/ordered]
     [org.clojure/test.check]
     [com.gfredericks/test.chuck]
     [riddley]
     [clj-commons/clj-yaml]
     [org.yaml/snakeyaml]

     ;; Only needed for :integration tests
     [org.openvoxproject/trapperkeeper-filesystem-watcher]]))

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

(def pdb-aot-classes
  ;; Compile classes first for now:
  ;; https://codeberg.org/leiningen/leiningen/issues/99
  '[puppetlabs.puppetdb.jdbc.PDBBytea puppetlabs.puppetdb.jdbc.VecPDBBytea])

(def pdb-aot-namespaces
  (into []
        (concat pdb-aot-classes
                [#"puppetlabs\.puppetdb\..*"]
                (->> "resources/puppetlabs/puppetdb/bootstrap.cfg"
                     clojure.java.io/reader
                     line-seq
                     (map clojure.string/trim)
                     (remove #(re-matches #"#.*" %)) ;; # comments
                     (remove #(re-matches #"puppetlabs\.puppetdb\.." %))
                     (map #(clojure.string/replace % #"(.*)/[^/]+$" "$1"))
                     (map symbol)))))

;; Avoid startup reflection warnings due to
;; https://clojure.atlassian.net/browse/CLJ-2066
;; https://openjdk.java.net/jeps/396
(def pdb-jvm-opts (when (< 8 (:feature pdb-jvm-ver) 17)
                    ["--illegal-access=deny"]))

(def i18n-version "1.0.3")
(def jackson-version "2.20.1")
(def slf4j-version "2.0.17")
(defproject org.openvoxproject/puppetdb pdb-version
  :description "OpenVox-integrated catalog and fact storage"

  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :url "https://github.com/openvoxproject/openvoxdb/"

  :min-lein-version "2.7.1"

  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort

  ;; These are to enforce consistent versions across dependencies of dependencies,
  ;; and to avoid having to define versions in multiple places. If a component
  ;; defined under :dependencies ends up causing an error due to :pedantic? :abort,
  ;; because it is a dep of a dep with a different version, move it here.
  :managed-dependencies [[org.clojure/clojure "1.12.4"]
                         [org.clojure/core.async "1.5.648"]
                         [org.clojure/core.match "1.1.1"]
                         [org.clojure/core.memoize "1.0.257"]
                         [org.clojure/data.generators "1.1.1"]
                         [org.clojure/java.jdbc "0.7.12"]
                         [org.clojure/math.combinatorics "0.3.2"]
                         [org.clojure/test.check "1.1.3"]
                         [org.clojure/tools.logging "1.2.4"]
                         [org.clojure/tools.macro "0.2.2"]
                         [org.clojure/tools.namespace "0.2.11"]
                         [org.clojure/tools.nrepl "0.2.13"]
                         [org.clojure/tools.reader "1.3.6"]
                         [bidi "2.1.6"]
                         [cheshire "5.13.0"]
                         [clj-commons/clj-yaml "1.0.29"]
                         [clj-commons/fs "1.6.312"]
                         [clj-http "3.13.1"]
                         [clj-kondo "2025.10.23"]
                         [clj-stacktrace "0.2.8"]
                         [clj-time "0.15.2"]
                         [com.fasterxml.jackson.core/jackson-core ~jackson-version]
                         [com.fasterxml.jackson.core/jackson-databind ~jackson-version]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor ~jackson-version]
                         [com.fasterxml.jackson.dataformat/jackson-dataformat-smile ~jackson-version]
                         [com.fasterxml.jackson.module/jackson-module-afterburner ~jackson-version]
                         ;; For some reason, this version is 2.20 without a .1. Update this back to
                         ;; ~jackson-version when they match again.
                         [com.fasterxml.jackson.core/jackson-annotations "2.20"]
                         [com.gfredericks/test.chuck "0.2.15"]
                         [com.github.seancorfield/honeysql "2.3.911"]
                         [com.github.seancorfield/next.jdbc "1.3.1086"]
                         [com.rpl/specter "1.1.6"]
                         [com.taoensso/nippy "3.6.0" :exclusions [org.tukaani/xz]]
                         [com.zaxxer/HikariCP "5.1.0"]
                         [commons-codec "1.20.0"]
                         [commons-io "2.21.0"]
                         [compojure "1.7.2"]
                         [digest "1.4.10"]
                         [fast-zip "0.4.0"]
                         [instaparse "1.5.0"]
                         [joda-time "2.12.5"]
                         [metrics-clojure "2.10.0"]
                         [murphy "0.5.3"]
                         [net.logstash.logback/logstash-logback-encoder "7.3"]
                         [org.apache.commons/commons-lang3 "3.20.0"]
                         [org.bouncycastle/bcpkix-jdk18on "1.83"]
                         [org.bouncycastle/bcpkix-fips "1.0.8"]
                         [org.bouncycastle/bc-fips "1.0.2.6"]
                         [org.bouncycastle/bctls-fips "1.0.19"]
                         [org.flatland/ordered "1.15.12"]
                         [org.slf4j/slf4j-api ~slf4j-version]
                         [org.slf4j/jul-to-slf4j ~slf4j-version]
                         [org.slf4j/log4j-over-slf4j ~slf4j-version]
                         [org.openvoxproject/comidi "1.1.2"]
                         [org.openvoxproject/i18n ~i18n-version]
                         [org.openvoxproject/kitchensink "3.5.5"]
                         [org.openvoxproject/kitchensink "3.5.5" :classifier "test"]
                         [org.openvoxproject/ssl-utils "3.6.2"]
                         [org.openvoxproject/stockpile "1.0.1"]
                         [org.openvoxproject/structured-logging "1.0.1"]
                         [org.openvoxproject/trapperkeeper "4.3.2"]
                         [org.openvoxproject/trapperkeeper "4.3.2" :classifier "test"]
                         [org.openvoxproject/trapperkeeper-authorization "2.1.5"]
                         [org.openvoxproject/trapperkeeper-authorization "2.1.5" :exclusions [io.dropwizard.metrics/metrics-core]]
                         [org.openvoxproject/trapperkeeper-filesystem-watcher "1.5.1"]
                         [org.openvoxproject/trapperkeeper-metrics "2.1.6"]
                         [org.openvoxproject/trapperkeeper-metrics "2.1.6" :exclusions [io.dropwizard.metrics/metrics-core]]
                         [org.openvoxproject/trapperkeeper-status "1.3.1"]
                         [org.openvoxproject/trapperkeeper-status "1.3.1" :exclusions [io.dropwizard.metrics/metrics-core]]
                         [org.openvoxproject/trapperkeeper-webserver-jetty10 "1.1.3"]
                         [org.openvoxproject/trapperkeeper-webserver-jetty10 "1.1.3" :classifier "test"]
                         [org.postgresql/postgresql "42.7.9"]
                         [org.yaml/snakeyaml "2.0"]
                         [prismatic/schema "1.4.1"]
                         [riddley "0.2.0"]
                         [ring/ring-codec "1.3.0"]
                         [ring/ring-core "1.8.2"]
                         [ring/ring-mock "0.4.0"]
                         [robert/hooke "1.3.0"]
                         [timofreiberg/bultitude "0.3.1"]
                         [trptcolin/versioneer "0.2.0"]]

  :dependencies [[org.clojure/clojure]
                 [org.clojure/core.async]
                 [org.clojure/core.match]
                 [org.clojure/core.memoize]
                 [org.clojure/data.generators]
                 [org.clojure/java.jdbc]
                 [org.clojure/math.combinatorics]
                 [org.clojure/tools.logging]
                 [org.clojure/tools.macro]
                 [org.clojure/tools.namespace]
                 [org.clojure/tools.nrepl]
                 [bidi]
                 [cheshire]
                 [clj-commons/fs]
                 [clj-http]
                 [clj-stacktrace]
                 [clj-time]
                 [com.fasterxml.jackson.core/jackson-databind]
                 [com.github.seancorfield/honeysql]
                 [com.github.seancorfield/next.jdbc]
                 [com.rpl/specter]
                 [com.taoensso/nippy :exclusions [org.tukaani/xz]]
                 [com.zaxxer/HikariCP]
                 [commons-io]
                 [compojure]
                 [digest]
                 [fast-zip]
                 [instaparse]
                 [metrics-clojure]
                 [murphy]
                 ;; We do not currently use this dependency directly, but
                 ;; we have documentation that shows how users can use it to
                 ;; send their logs to logstash, so we include it in the jar.
                 [net.logstash.logback/logstash-logback-encoder]
                 [org.apache.commons/commons-lang3]
                 [org.openvoxproject/comidi]
                 [org.openvoxproject/i18n]
                 [org.openvoxproject/kitchensink]
                 [org.openvoxproject/ssl-utils]
                 [org.openvoxproject/stockpile]
                 [org.openvoxproject/structured-logging]
                 [org.openvoxproject/trapperkeeper]
                 [org.openvoxproject/trapperkeeper-authorization]
                 [org.openvoxproject/trapperkeeper-metrics]
                 [org.openvoxproject/trapperkeeper-status]
                 [org.openvoxproject/trapperkeeper-webserver-jetty10]
                 [org.postgresql/postgresql]
                 [ring/ring-core]
                 [robert/hooke]
                 [trptcolin/versioneer]]

  :jvm-opts ~pdb-jvm-opts

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/CLOJARS_USERNAME
                                     :password :env/CLOJARS_PASSWORD
                                     :sign-releases false}]]

  :plugins [[lein-release "1.1.3" :exclusions [org.clojure/clojure]]
            [lein-cloverage "1.2.4"]
            [org.openvoxproject/i18n ~i18n-version]]

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

  :aot ~pdb-aot-classes

  :profiles {
    :defaults {:resource-paths ["test-resources"]
                        :dependencies ~pdb-dev-deps
                        :injections [(do
                                       (require 'schema.core)
                                       (schema.core/set-fn-validation! true))]}

    :dev-settings {:dependencies [[org.bouncycastle/bcpkix-jdk18on]]
                    :plugins [[jonase/eastwood "1.4.3"]]
                    :injections [(require 'test-summary)]
                    :jvm-opts ~(conj pdb-jvm-opts "-XX:-OmitStackTraceInFastThrow")}

    :dev [:defaults :dev-settings]

    :fips-settings {:dependencies [[org.bouncycastle/bcpkix-fips]
                                    [org.bouncycastle/bc-fips]
                                    [org.bouncycastle/bctls-fips]]

                    :vars {:java-args ~(str "-Xmx192m "
                                            "-Djdk.tls.ephemeralDHKeySize=2048 "
                                            "-Djava.security.properties==/opt/puppetlabs/server/data/puppetdb/java.security.fips")}

                    ;; this only ensures that we run with the proper profiles
                    ;; during testing. This JVM opt will be set in the puppet module
                    ;; that sets up the JVM classpaths during installation.
                    :jvm-opts ~(let [{:keys [feature interim]} pdb-jvm-ver]
                                  (conj pdb-jvm-opts
                                        (case feature
                                          17 "-Djava.security.properties==resources/ext/build-scripts/java.security.fips"
                                          21 "-Djava.security.properties==resources/ext/build-scripts/java.security.fips"
                                          (do))))}

    :fips [:defaults :fips-settings]

    :kondo {:dependencies [[clj-kondo]]}
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
                                      [org.bouncycastle/bcpkix-jdk18on]

                                      ;; we need to explicitly pull in our parent project's
                                      ;; clojure version here, because without it, lein
                                      ;; brings in its own version, and older versions of
                                      ;; lein depend on clojure 1.6.
                                      [org.clojure/clojure]

                                      ;; This circular dependency is required because of a bug in
                                      ;; ezbake (EZ-35); without it, bootstrap.cfg will not be included
                                      ;; in the final package.
                                      [org.openvoxproject/puppetdb ~pdb-version]]
              :name "puppetdb"
              :plugins [[org.openvoxproject/lein-ezbake ~(or (System/getenv "EZBAKE_VERSION") "2.7.1")]]}
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
