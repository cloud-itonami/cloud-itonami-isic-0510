(ns mining.store
  "CoalMiningStore — the persistent state contract for hard coal mining
  operations.")

(defprotocol Store
  "A Store persists mining operations and site registration state."
  (site [s site-id]
    "Query a registered mine site by ID. Returns site map or nil.
    Site map must include: :site-id, :client-id, :permitted?")
  (commit-record! [s record]
    "Atomically commit an operation record.")
  (append-ledger! [s entry]
    "Append an audit ledger entry (immutable event)."))

(defn mem-store
  "An in-memory Store for testing."
  [sites]
  (let [records (atom [])
        ledger (atom [])]
    (reify Store
      (site [_ site-id]
        (get sites site-id))
      (commit-record! [_ record]
        (swap! records conj record))
      (append-ledger! [_ entry]
        (swap! ledger conj entry)))))
