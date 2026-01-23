(ns puppetlabs.puppetdb.meta.version
  "Versioning Utility Library

   This namespace contains some utility functions relating to checking version
   numbers of various fun things."
   (:require [trptcolin.versioneer.core :as version]))

;; ### PuppetDB current version

(defn version
  "Get the version number of this PuppetDB installation."
  []
  {:post [(string? %)]}
  (version/get-version "org.openvoxproject" "puppetdb"))
