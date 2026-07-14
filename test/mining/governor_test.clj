(ns mining.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [mining.governor :as gov]
            [mining.store :as store]))

(def site-1
  {:site-id "coal-mine-1"
   :client-id "client-A"
   :permitted? true
   :name "Northern Coal Mine"})

(def site-2
  {:site-id "coal-mine-2"
   :client-id "client-B"
   :permitted? true
   :name "Eastern Coal Mine"})

(deftest site-registration-hard-check
  (testing "unregistered site is a hard violation"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          request {:client-id "client-A"}
          proposal {:site-id "unknown-site" :op :log-production-record :effect :propose :confidence 0.8}
          verdict (gov/check request nil proposal s)]
      (is (true? (:hard? verdict)))
      (is (> (count (:violations verdict)) 0)))))

(deftest no-direct-actuation-hard-check
  (testing "direct actuation (non-:propose) is a hard violation"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          request {:client-id "client-A"}
          proposal {:site-id "coal-mine-1" :op :log-production-record :effect :commit :confidence 0.8}
          verdict (gov/check request nil proposal s)]
      (is (true? (:hard? verdict))))))

(deftest site-ownership-hard-check
  (testing "site owned by different client is a hard violation"
    (let [s (store/mem-store {:coal-mine-1 site-1 :coal-mine-2 site-2})
          request {:client-id "client-A"}
          proposal {:site-id "coal-mine-2" :op :log-production-record :effect :propose :confidence 0.8}
          verdict (gov/check request nil proposal s)]
      (is (true? (:hard? verdict))))))

(deftest safety-flag-escalates
  (testing "flag-safety-concern always escalates"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          request {:client-id "client-A"}
          proposal {:site-id "coal-mine-1" :op :flag-safety-concern :effect :propose :confidence 0.9}
          verdict (gov/check request nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-confidence-escalates
  (testing "low confidence (<0.6) escalates"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          request {:client-id "client-A"}
          proposal {:site-id "coal-mine-1" :op :log-production-record :effect :propose :confidence 0.5}
          verdict (gov/check request nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest happy-path-logging
  (testing "valid production logging passes all checks"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          request {:client-id "client-A"}
          proposal {:site-id "coal-mine-1" :op :log-production-record :effect :propose :confidence 0.8}
          verdict (gov/check request nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (false? (:escalate? verdict)))
      (is (true? (:ok? verdict))))))

(deftest happy-path-maintenance
  (testing "valid maintenance scheduling passes all checks"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          request {:client-id "client-A"}
          proposal {:site-id "coal-mine-1" :op :schedule-maintenance :effect :propose :confidence 0.7}
          verdict (gov/check request nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (false? (:escalate? verdict)))
      (is (true? (:ok? verdict))))))

(deftest happy-path-shipment
  (testing "valid shipment coordination passes all checks"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          request {:client-id "client-A"}
          proposal {:site-id "coal-mine-1" :op :coordinate-shipment :effect :propose :confidence 0.75}
          verdict (gov/check request nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (false? (:escalate? verdict)))
      (is (true? (:ok? verdict))))))
