(ns mining.governor
  "CoalMiningGovernor — the independent safety/traceability layer for the
  ISIC-Rev4 0510 hard coal mining operations-coordination actor.

  CRITICAL SCOPE EXCLUSIONS: This governor does NOT and NEVER WILL govern:
  - Direct extraction sequencing (blasting order, drilling, cutting schedules)
  - Mine safety decisions (ventilation, gas monitoring, structural stability)
  - Mining authority decisions (permit issuance, license suspension)

  HARD invariants: site-registration, no-direct-actuation, site-ownership.
  ESCALATION invariants: :flag-safety-concern (always), low confidence (< 0.6)."
  (:require [mining.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} site-record]
  (let [{:keys [client-id]} request
        {:keys [site-id effect]} proposal]
    (cond-> []
      (nil? site-record)
      (conj {:rule :site-unregistered :detail "site 未登録（許可台帳外）"})

      (not= :propose effect)
      (conj {:rule :no-direct-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and site-record (not= (:client-id site-record) client-id))
      (conj {:rule :site-wrong-owner :detail "site が別 client のもの"}))))

(defn check
  "Assess a proposal against request/context/proposal and store."
  [request context proposal store]
  (let [site-record (some->> (:site-id proposal) (store/site store))
        hard (hard-violations {:request request :proposal proposal}
                              site-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        safety-flag? (= :flag-safety-concern (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not safety-flag?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? safety-flag?))}))
