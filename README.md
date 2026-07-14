# cloud-itonami-isic-0510

Open Business Blueprint for **ISIC Rev.4 0510**: Mining of hard coal —
an ISIC Wave 3 (production/mining) operations-coordination actor per
ADR-2607121000. Back-office and coordination workflow for hard coal
mining operations, modeled closely on `cloud-itonami-isic-0891`'s
(Mining of chemical and fertilizer minerals) governed-actor discipline.

**Maturity: `:implemented`** — CoalOpsAdvisor ⊣ CoalMiningGovernor as a
langgraph-clj StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt). All source `.cljc` (portable
to JVM / ClojureScript / GraalVM), no JVM-only interop.

## CRITICAL: Scope Exclusions

This actor **DOES NOT** and **NEVER WILL**:

- **Direct extraction sequencing** — blasting order, drilling patterns, or cutting schedules
- **Mine-safety decisions** — ventilation control, gas-monitoring overrides, structural-stability determinations
- **Mine-safety-authority decisions** — permit issuance, license suspension, or compliance enforcement

This actor **only** coordinates back-office operations: production-record
logging, maintenance scheduling, safety-concern flagging (always routed
to a human), and outbound coal-shipment coordination. Every proposal the
advisor drafts carries `:effect :propose` — never a direct actuation —
and `coalops.governor` independently re-scans every proposal's content
for the excluded scope areas above, regardless of op or confidence.

## Operations

Closed proposal-op allowlist (`coalops.governor/allowed-ops`), all
`:effect :propose`:

- `:log-production-record` — output/tonnage data logging
- `:schedule-maintenance` — equipment maintenance scheduling proposal
- `:flag-safety-concern` — surface a mine-safety concern (gas, structural, ventilation) — **ALWAYS escalates**
- `:coordinate-shipment` — outbound coal shipment coordination

**HARD invariants** (always `:hold`, never human-overridable):

1. **Site unverified** — the target mine/site record must exist AND be
   independently `:registered?`/`:verified?` in the store before any
   proposal for it may commit or even escalate.
2. **Effect not `:propose`** — any proposal whose `:effect` is not
   `:propose` is, by construction, a claim to directly actuate outside
   governance.
3. **Scope exclusion** — any proposal (regardless of op) outside the
   closed allowlist, or whose rationale/summary/citations/value touches
   blasting/extraction-sequencing/mine-safety-authority territory, is a
   permanent, un-overridable block. Evaluated unconditionally on every
   proposal.

**ESCALATE** (always human sign-off, when the governor is otherwise clean):

- `:flag-safety-concern` — always, regardless of confidence.
- Low advisor confidence (`< 0.6`).

## Rollout phases (`coalops.phase`)

Phase 0 (read-only) → 1 (production logging, approval-gated) → 2 (adds
maintenance + shipment coordination, approval-gated) → 3 (supervised
auto: production-record/maintenance/shipment may auto-commit when
governor-clean and confident). `:flag-safety-concern` is deliberately
absent from every phase's `:auto` set — a permanent structural fact,
not a rollout milestone still to come — matching `coalops.governor`'s
own `always-escalate-ops` independently.

## Development

```bash
clojure -M:test   # run the full suite
clojure -M:run    # walk the demo scenarios (coalops.sim)
clojure -M:lint    # clj-kondo
```

AGPL-3.0-or-later, forkable by any qualified operator. Part of cloud-itonami.
