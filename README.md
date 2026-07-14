# cloud-itonami-isic-0510

Open Business Blueprint for **ISIC Rev.4 0510**: Mining of hard coal —
an ISIC Wave 3 (production/mining) operations-coordination actor per
ADR-2607121000. Back-office and coordination workflow for hard coal mining
operations.

**Maturity: `:implemented`** — CoalMiningAdvisor ⊣ CoalMiningGovernor
as a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt).

## CRITICAL: Scope Exclusions

This actor **DOES NOT** and **NEVER WILL**:
- **Direct extraction sequencing** — blasting order, drilling patterns, or cutting schedules
- **Mine safety decisions** — ventilation, gas monitoring, structural stability
- **Mining authority decisions** — permit issuance, license suspension, or compliance enforcement

This actor **only** coordinates back-office operations: production logging, maintenance scheduling, safety-concern escalation (human-flagged), and outbound coal movement.

## Operations

HARD invariants: site registration, no-direct-actuation, site-ownership.
Escalations: safety concerns, low-confidence proposals.

AGPL-3.0-or-later, forkable by any qualified operator. Part of cloud-itonami.
