(ns puppetlabs.puppetdb.cli.util-test
  (:require
   [clojure.test :refer [deftest is]]
   [puppetlabs.puppetdb.cli.util :refer [jdk-support-status]]))

(deftest jdk-support-status-behavior
  (is (= :unsupported (jdk-support-status "1.5")))
  (is (= :unsupported (jdk-support-status "1.5.0")))
  (is (= :unsupported (jdk-support-status "1.6")))
  (is (= :unsupported (jdk-support-status "1.6.0")))
  (is (= :unknown (jdk-support-status "1.60")))
  (is (= :unknown (jdk-support-status "1.60.1")))
  (is (= :unknown (jdk-support-status "huh?")))
  (is (= :unsupported (jdk-support-status "1.7")))
  (is (= :unsupported (jdk-support-status "1.7.0")))
  (is (= :unsupported (jdk-support-status "1.8")))
  (is (= :unsupported (jdk-support-status "1.8.0")))
  (is (= :unsupported (jdk-support-status "1.9")))
  (is (= :unsupported (jdk-support-status "1.9.0")))
  (is (= :unsupported (jdk-support-status "10")))
  (is (= :unsupported (jdk-support-status "10.0")))
  (is (= :unsupported (jdk-support-status "11.0")))
  (is (= :unsupported (jdk-support-status "11.0.7")))
  (is (= :unsupported (jdk-support-status "17.0.4")))
  (is (= :official (jdk-support-status "21")))
  (is (= :official (jdk-support-status "21.0")))
  (is (= :official (jdk-support-status "21.0.4"))))
