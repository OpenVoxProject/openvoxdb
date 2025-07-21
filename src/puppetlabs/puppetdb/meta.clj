(ns puppetlabs.puppetdb.meta
  (:require [puppetlabs.puppetdb.middleware :as mid]
            [puppetlabs.puppetdb.http :as http]
            [puppetlabs.puppetdb.meta.version :as v]
            [puppetlabs.puppetdb.time :refer [now]]
            [puppetlabs.ring-middleware.core :as rmc]
            [puppetlabs.comidi :as cmdi]
            [bidi.schema :as bidi-schema]
            [puppetlabs.puppetdb.schema :as pls]
            [puppetlabs.i18n.core :refer [tru]]))

(defn current-version-fn
  "Returns a function that always returns a JSON object with the running
   version of PDB."
  [version]
  (fn [_]
    (if version
      (http/json-response {:version version})
      (http/error-response
       (tru "Could not find version") 404))))

(pls/defn-validated meta-routes :- bidi-schema/RoutePair
  []
  (cmdi/context "/v1"
                (cmdi/context "/version"
                              (cmdi/ANY "" []
                                        (current-version-fn (v/version))))
                (cmdi/ANY "/server-time" []
                          (http/json-response {:server_time (now)}))))

(defn build-app
  []
  (-> (meta-routes)
      mid/make-pdb-handler
      rmc/wrap-accepts-json
      mid/validate-no-query-params))
