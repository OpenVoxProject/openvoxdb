(ns puppetlabs.puppetdb.meta-test
  (:require [puppetlabs.puppetdb.cheshire :as json]
            [clojure.test :refer :all]
            [puppetlabs.puppetdb.testutils
             :refer [assert-success! dotestseq get-request]]
            [puppetlabs.puppetdb.meta :as meta]
            [puppetlabs.puppetdb.time
             :refer [ago seconds in-seconds interval parse-wire-datetime]]
            [puppetlabs.puppetdb.middleware :as mid]))

(def endpoints [[:v1 "/v1"]])

(def parsed-body
  "Returns clojure data structures from the JSON body of
   ring response."
  (comp #(json/parse-string % true) :body))

(defn with-meta-app
  [request]
  (let [app (mid/wrap-with-puppetdb-middleware (meta/build-app))]
    (app request)))

(deftest server-time-response
  (dotestseq [[_version endpoint] endpoints]
    (let [test-time (-> 1 seconds ago)
          response (-> (get-request (str endpoint "/server-time"))
                       with-meta-app)]
      (assert-success! response)
      (let [server-time (-> response
                            parsed-body
                            :server_time
                            parse-wire-datetime)]
        (is (> (in-seconds (interval test-time server-time)) 0))
        (is (> 5 (in-seconds (interval test-time server-time))))))))
