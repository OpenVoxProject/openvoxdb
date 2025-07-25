(ns puppetlabs.puppetdb.testutils.dashboard)

(defn dashboard-base-url->str
  "Similar to puppetlabs.puppetdb.utils/base-url->str but doesn't
  include a version as the dashboard page does not include a version"
  [{:keys [protocol host port prefix] :as _base-url}]
  (-> (java.net.URL. protocol host port prefix)
      .toURI .toASCIIString))

(defn dashboard-page? [{:keys [body] :as _req}]
  (.contains body "<title>OpenvoxDB: Dashboard</title>"))
