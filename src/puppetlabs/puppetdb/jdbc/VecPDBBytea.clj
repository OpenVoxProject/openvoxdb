(ns puppetlabs.puppetdb.jdbc.VecPDBBytea
  "Carrier for bytea[] parameters, to support clojure.java.jdbc and next.jdbc
  protocol extensions. Essentially just a typed wrapper around a byte[][]."
  ;; It doesn't look like java.sql.Arrays, which is what this
  ;; replaces, define content-based equals(), so we don't need one.
  (:gen-class
   :state ^"[[B" data
   :init init
   :constructors {["[[B"] []}))

(def ^:private warn-on-reflection-orig *warn-on-reflection*)
(set! *warn-on-reflection* true)

(defn -init [data] [[] data])

(set! *warn-on-reflection* warn-on-reflection-orig)
