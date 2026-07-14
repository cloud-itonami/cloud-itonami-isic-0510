(ns mining.advisor
  "CoalMiningAdvisor — the LLM-driven proposal phase for hard coal mining
  operations. Transforms a mining operation request into a concrete proposal
  that the independent CoalMiningGovernor will assess.")

(defprotocol Advisor
  "An Advisor transforms requests into proposals."
  (-advise [advisor store request]
    "Given a mining request, return a concrete proposal map or nil."))

(defn mock-advisor
  "A mock advisor that returns a canned proposal structure."
  []
  (reify Advisor
    (-advise [_ _ request]
      (let [{:keys [client-id site-id op]} request]
        {:client-id client-id
         :site-id site-id
         :op op
         :effect :propose
         :confidence 0.8}))))
