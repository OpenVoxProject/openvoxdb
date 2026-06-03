(ns puppetlabs.puppetdb.cli.util
  "As this namespace is required by both the tk and non-tk subcommands,
  it must remain very lightweight, so that subcommands like
  \"version\" aren't slowed down by loading the entire logging
  subsystem or trapperkeeper, etc."
  (:require
   [puppetlabs.i18n.core :refer [trs]]))

(def err-exit-status 2)

;; Testing hook
(defn java-version [] (System/getProperty "java.version"))

(defn jdk-support-status
  "Returns :official, :tested, :deprecated, :unknown, or :unsupported."
  [version]
  (cond
    (re-matches #"1\.[1234567]($|(\..*))" version) :unsupported
    (re-matches #"1\.[89]($|(\..*))" version) :unsupported
    (re-matches #"10($|(\..*))" version) :unsupported
    (re-matches #"11($|(\..*))" version) :unsupported
    (re-matches #"17($|(\..*))" version) :unsupported
    (re-matches #"21($|(\..*))" version) :official
    (re-matches #"25($|(\..*))" version) :official
    :else :unknown))

(defn jdk-unsupported-msg [version]
  (let [status (jdk-support-status version)]
    (case status
      (:unknown) {:warn (trs "JDK {0} is neither tested nor supported. Please use JDK 21 or 25" version)}
      (:deprecated) {:warn (trs "JDK {0} is deprecated, please upgrade to JDK 21 or 25" version)}
      (:official :tested) nil
      {:error (trs "PuppetDB doesn''t support JDK {0}" version)})))

(defn run-cli-cmd [f]
  (let [jdk (java-version)]
    (if-let [{:keys [warn error]} (jdk-unsupported-msg jdk)]
      (if error
        (do
          (binding [*out* *err*] (println (trs "error:") error))
          err-exit-status)
        (do
          (binding [*out* *err*] (println (trs "warn:") warn))
          (f)))
      (f))))

(defn exit [status]
  (shutdown-agents)
  (binding [*out* *err*] (flush))
  (flush)
  (System/exit status))
