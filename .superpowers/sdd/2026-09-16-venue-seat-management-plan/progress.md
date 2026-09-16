# SDD ledger — plan: docs/superpowers/plans/2026-09-16-venue-seat-management-plan.md

## Preflight scan

| Scope | Relationship checked | Result | Ruling |
|---|---|---|---|
| Task 1 ↔ Task 2 | ActivityCatalog seat-template methods consumed by controllers | Compatible signatures; Task 2 must use Task 1 records | Proceed in order |
| Task 2 ↔ Task 3 | REST paths and payloads consumed by adminApi.ts | Paths/payloads explicit and compatible | Proceed in order |
| Task 3 ↔ Task 4 | API function names consumed by AdminLayout.vue/tests | Names are defined before UI integration | Proceed in order |
| Task 4 ↔ Task 5 | UI behavior and integration evidence | Task 5 verifies outputs from Task 4 | Proceed in order |
| Task 1 | Migration, service methods, tests | Self-consistent; template seats copied to sessions | Proceed |
| Task 2 | Controllers, permissions, audit tests | Self-consistent; uses existing headers | Proceed |
| Task 3 | API wrappers and Vitest | Self-consistent; UI assertions deferred to Task 4 | Proceed |
| Task 4 | Vue state, modal, date validation | Self-consistent; depends on Task 3 wrappers | Proceed |
| Task 5 | Build, Docker, evidence docs | Self-consistent; no code dependencies beyond prior tasks | Proceed |

## Task 1 execution

- RED: `mvn --% -q -pl services/activity-service -am -Dtest=VenueSeatTemplateTest -Dsurefire.failIfNoSpecifiedTests=false test` initially failed because the new API was missing and the existing `Venue` constructor had changed.
- GREEN: Added `V6__venue_seat_template.sql`, `VenueSeat` CRUD methods, session time/venue validation, and template-to-session seat copy. Focused test now passes 3/3.

## Decisions

Ruling: venue seat templates are copied into each session at session creation — preserves independent inventory per session and avoids retroactively changing sold seats.
