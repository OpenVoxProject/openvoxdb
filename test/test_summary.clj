(ns test-summary
  (:require [clojure.test :as t]))

(def ^:private failures (atom []))

(defonce ^:private original-report-fail (get-method t/report :fail))
(defonce ^:private original-report-error (get-method t/report :error))
(defonce ^:private original-report-summary (get-method t/report :summary))

(defmethod t/report :fail [m]
  (swap! failures conj (assoc m
                              :test-var (first t/*testing-vars*)
                              :contexts t/*testing-contexts*))
  (original-report-fail m))

(defmethod t/report :error [m]
  (swap! failures conj (assoc m
                              :test-var (first t/*testing-vars*)
                              :contexts t/*testing-contexts*))
  (original-report-error m))

(defmethod t/report :summary [m]
  (original-report-summary m)
  (when (seq @failures)
    (println "\n\n========== FAILURE SUMMARY ==========\n")
    (doseq [{:keys [type test-var message expected actual file line]} @failures]
      (println (str (name type) ": "
                    (when test-var
                      (str (-> test-var meta :ns) "/" (-> test-var meta :name)))))
      (when (and file line)
        (println (str "  at " file ":" line)))
      (when message
        (println (str "  " message)))
      (println (str "  expected: " (pr-str expected)))
      (println (str "    actual: " (pr-str actual)))
      (println))
    (println "======================================\n"))
  (reset! failures []))
