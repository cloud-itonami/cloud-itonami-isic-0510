(ns coalops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`coalops.operation` -> `coalops.governor` -> `coalops.store`) through
  a scenario adapted from this repo's own `coalops.sim` demo driver
  (`clojure -M:dev:run`, confirmed to run correctly against the real
  seeded site directory before this file was written -- unlike
  `cloud-itonami-isic-851`'s `schoolops.sim`, this repo's own sim
  driver uses ids that DO match `coalops.store/demo-data`
  (coal-mine-1/coal-mine-2/coal-mine-3), so it was safe to reuse rather
  than author from scratch), trimmed to a representative subset (three
  clean phase-3 auto-commits, one always-escalate safety-concern flag
  approved by a human, and three distinct HARD-hold reasons) and
  rendered deterministically -- no invented numbers, no timestamps in
  the page content, byte-identical across reruns against the same seed
  (verified by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [coalops.store :as store]
            [coalops.operation :as op]
            [coalops.advisor :as advisor]
            [langgraph.graph :as g]))

;; ----------------------------- harness -----------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :shift-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: coal-mine-1 clears three ops that all auto-commit
  clean at phase 3 (log-production-record, schedule-maintenance,
  coordinate-shipment); coal-mine-1's safety-concern flag ALWAYS
  escalates (per `governor/always-escalate-ops`) even though clean and
  high-confidence, and is approved by a human shift supervisor;
  coal-mine-2 (registered AND verified, so both checks below are
  exercised in isolation, not conflated with a site-verification
  failure) sees an advisor that drafts a non-`:propose` `:effect` (an
  attempted direct actuation) HARD-blocked on `:effect-not-propose`,
  then a proposal that has drifted into the permanently-excluded
  blasting/extraction-sequencing scope HARD-blocked on
  `:scope-excluded`; coal-mine-3 (registered but NOT `:verified?` in
  the seed data) HARD-holds on `:site-unverified`. None of the HARD
  holds ever reach a human. Returns the resulting store -- every field
  read by `render` below is real governor/store output, not a
  hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        rogue-actor (op/build db {:advisor (reify advisor/Advisor
                                             (-advise [_ st req]
                                               (assoc (advisor/infer st req) :effect :commit)))})]
    (exec! actor "coal1-production" {:op :log-production-record :site-id "coal-mine-1"
                                      :patch {:tonnage 4200 :shift "day"}})
    (exec! actor "coal1-maintenance" {:op :schedule-maintenance :site-id "coal-mine-1"
                                       :patch {:equipment "conveyor-belt-3" :window "2026-08-01"}})
    (exec! actor "coal1-shipment" {:op :coordinate-shipment :site-id "coal-mine-1"
                                    :patch {:carrier "rail-co-1" :tonnage 4200}})

    (exec! actor "coal1-safety" {:op :flag-safety-concern :site-id "coal-mine-1"
                                  :patch {:concern "elevated methane reading" :confidence 0.95}})
    (approve! actor "coal1-safety")

    (exec! rogue-actor "coal2-rogue-shipment" {:op :coordinate-shipment :site-id "coal-mine-2"
                                                :patch {:carrier "rail-co-1"}})

    (exec! actor "coal2-scope" {:op :schedule-maintenance :site-id "coal-mine-2"
                                 :out-of-scope? true :patch {}})

    (exec! actor "coal3-production" {:op :log-production-record :site-id "coal-mine-3"
                                      :patch {:tonnage 100}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger site-id]
  (last (filter #(= (:site-id %) site-id) ledger)))

(defn- status-cell [ledger site-id]
  (let [f (last-fact-for ledger site-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (case rule
          :site-unverified "<span class=\"critical\">HARD hold &middot; unverified site</span>"
          :effect-not-propose "<span class=\"critical\">HARD hold &middot; direct-actuation attempt</span>"
          :scope-excluded "<span class=\"critical\">HARD hold &middot; scope-excluded</span>"
          (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- site-row [ledger {:keys [site-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc site-id) (esc name)
          (if (and registered? verified?)
            "<span class=\"ok\">registered &amp; verified</span>"
            "<span class=\"warn\">registered, unverified</span>")
          (status-cell ledger site-id)))

(defn- ledger-row [{:keys [t op site-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc site-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops` table, `coalops.governor`/`coalops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-production-record</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        sites (store/all-sites db)
        site-rows (str/join "\n" (map (partial site-row ledger) sites))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0510 &middot; hard-coal-mining operations coordination</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Hard coal mining operations coordination (ISIC 0510) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never touches extraction/blasting/mine-safety-authority decisions</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Mine sites</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>coalops.store</code> via <code>coalops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site</th><th>Name</th><th>Registration status</th><th>Last coordination status</th></tr></thead>\n"
     "      <tbody>\n"
     site-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (CoalMiningGovernor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Extraction sequencing, blasting/drilling/cutting schedules, ventilation/gas-monitoring control and mine-safety-authority decisions are permanently out of scope — see governor scope-exclusion.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Site</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
