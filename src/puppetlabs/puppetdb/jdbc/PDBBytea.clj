(ns puppetlabs.puppetdb.jdbc.PDBBytea
  "Carrier for bytea parameters, to support clojure.java.jdbc and next.jdbc
  protocol extensions. Essentially just a typed wrapper around a byte[]."
  (:import
   (java.util Arrays))
  (:gen-class
   ;;:extends org.postgresql.util.PGobject
   :state ^"[B" data
   :init init
   :constructors {["[B"] []}))

(def ^:private warn-on-reflection-orig *warn-on-reflection*)
(set! *warn-on-reflection* true)

(defn -init [data] [[] data])

;; Implemented for now because edge-replacement-differential test, for
;; example, uses munge-hash-for-storage to create the test values that
;; it compares for equality -- and who knows what else. Apparently
;; PGobject bytea values, which PDBBytea replaces, compare content for
;; equality, and since we use bytea for hashes, best to be
;; conservative.
(defn -equals [^puppetlabs.puppetdb.jdbc.PDBBytea this x]
  (and (instance? puppetlabs.puppetdb.jdbc.PDBBytea x)
       (Arrays/equals ^"[B" (.data this)
                      ^"[B" (.data ^puppetlabs.puppetdb.jdbc.PDBBytea x))))

(defn -hashCode [^puppetlabs.puppetdb.jdbc.PDBBytea this]
  (Arrays/hashCode ^"[B" (.data this)))

(set! *warn-on-reflection* warn-on-reflection-orig)
