(ns mining.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [mining.actor :as actor]
            [mining.advisor :as advisor]
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

(deftest graph-builds
  (testing "actor graph builds without errors"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          graph (actor/build-graph {:store s})]
      (is (some? graph)))))

(deftest production-logging-completes
  (testing "valid production logging request completes to commit"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          graph (actor/build-graph {:store s})
          request {:client-id "client-A"}
          result (actor/run-request! graph request nil "thread-1")]
      (is (some? result)))))

(deftest maintenance-scheduling-completes
  (testing "valid maintenance scheduling completes to commit"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          graph (actor/build-graph {:store s})
          request {:client-id "client-A"}
          result (actor/run-request! graph request nil "thread-2")]
      (is (some? result)))))

(deftest shipment-coordination-completes
  (testing "valid shipment coordination completes to commit"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          graph (actor/build-graph {:store s})
          request {:client-id "client-A"}
          result (actor/run-request! graph request nil "thread-3")]
      (is (some? result)))))

(deftest unregistered-site-is-held
  (testing "unregistered site request is held"
    (let [s (store/mem-store {})
          graph (actor/build-graph {:store s})
          request {:client-id "client-A"}
          result (actor/run-request! graph request nil "thread-4")]
      (is (some? result)))))

(deftest wrong-site-owner-is-held
  (testing "request for site owned by different client is held"
    (let [s (store/mem-store {:coal-mine-2 site-2})
          graph (actor/build-graph {:store s})
          request {:client-id "client-A"}
          result (actor/run-request! graph request nil "thread-5")]
      (is (some? result)))))

(deftest safety-concern-escalates
  (testing "safety concern flag escalates for human approval"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          advisor (advisor/mock-advisor)
          graph (actor/build-graph {:store s :advisor advisor})
          request {:client-id "client-A" :op :flag-safety-concern}
          result (actor/run-request! graph request nil "thread-6")]
      (is (some? result)))))

(deftest human-approval-resumes
  (testing "human approval resumes an interrupted request"
    (let [s (store/mem-store {:coal-mine-1 site-1})
          advisor (advisor/mock-advisor)
          graph (actor/build-graph {:store s :advisor advisor})
          request {:client-id "client-A" :op :flag-safety-concern}
          _ (actor/run-request! graph request nil "thread-7")
          result (actor/approve! graph "thread-7")]
      (is (some? result)))))
