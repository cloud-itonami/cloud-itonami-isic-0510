(ns coalops.store
  "SSoT for the ISIC-0510 hard-coal-mining OPERATIONS-COORDINATION actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite -- the
  same seam every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the BACK OFFICE of a hard-coal mine: production
  logging, maintenance scheduling, safety-concern flagging and outbound
  shipment coordination. It never touches extraction sequencing, blasting/
  drilling/cutting schedules, ventilation or gas-monitoring control, or any
  mine-safety-authority decision (permit issuance, license suspension,
  compliance enforcement) -- see `coalops.governor`'s `scope-exclusion-
  violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `sites` directory keyed by `:site-id` STRING (never a
  keyword -- the prior scaffold attempt's tests keyed the seed map with
  keyword site-ids while every lookup used the string `:site-id` off the
  proposal, so `(get sites site-id)` silently missed on every call and
  masked itself as HARD site-unverified holds across 10 assertions; fixed
  here by keying consistently on the string).

  A registered/verified mine-site record must exist before ANY proposal
  for that site may ever commit or escalate -- `coalops.governor`'s
  `site-unverified-violations` re-derives this from the site's own
  `:registered?`/`:verified?` fields, never from proposal self-report,
  the SAME 'ground truth, not self-report' discipline every sibling
  actor's own governor uses (`chemmineops.registry/royalty-matches-
  claim?`'s discipline, reapplied here to site status).

  The ledger stays append-only: which site a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.")

(defprotocol Store
  (site [s site-id] "Registered mine/site record, or nil.
    Site map: {:site-id .. :name .. :registered? bool :verified? bool}.")
  (all-sites [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-sites [s sites] "replace/seed the site directory (map site-id->site)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained site directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:sites
   {"coal-mine-1" {:site-id "coal-mine-1" :name "Northern Coal Mine"
                   :registered? true :verified? true}
    "coal-mine-2" {:site-id "coal-mine-2" :name "Eastern Coal Mine"
                   :registered? true :verified? true}
    "coal-mine-3" {:site-id "coal-mine-3" :name "Southern Coal Mine (permit lapsed)"
                   :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (site [_ site-id] (get-in @a [:sites site-id]))
  (all-sites [_] (sort-by :site-id (vals (:sites @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-sites [s sites] (when (seq sites) (swap! a assoc :sites sites)) s))

(defn seed-db
  "A MemStore seeded with the demo site directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `sites` map (site-id string ->
  site map) -- the primary test/dev entry point. `sites` may be empty
  (an unregistered-everywhere store)."
  [sites]
  (->MemStore (atom {:sites (or sites {}) :ledger [] :coordination-log []})))
