# Real Data Booking Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Connect the Vue frontend to database-backed activities, sessions, seats, and order creation through the gateway.

**Architecture:** Activity-service owns MySQL activity/session/seat reads and seeds deterministic demo rows through Flyway. The frontend follows the API chain activity → sessions → seats → order and never fabricates business data.

**Tech Stack:** Spring Boot 3.5, JdbcTemplate, Flyway, MySQL 8.4, Vue 3, Vite, Vitest.

## Global Constraints

- Work only on the `master` branch.
- Preserve existing uncommitted changes.
- Business data shown in the UI must come from HTTP APIs backed by MySQL.

### Task 1: Database-backed activity/session/seat API

**Files:** activity-service datasource/Flyway config, migration V2 seed, catalog/repository/controller classes, tests.

- Add activity-service MySQL/Flyway dependencies and environment-driven datasource settings.
- Seed two activities, sessions, and seats in MySQL.
- Add `GET /api/activities/{id}` details with sessions and `GET /api/sessions/{id}/seats`.
- Keep unit-test fallback only for isolated catalog tests; production path requires JdbcTemplate.

### Task 2: Frontend booking flow

**Files:** `web/src/api.ts`, `web/src/types.ts`, `web/src/App.vue`, new booking view, tests.

- Bind activity cards to details loading.
- Render sessions from API, load seats from API, allow selecting only available seats.
- Submit selected seats to `/api/orders` using the authenticated API wrapper.
- Display API errors and order confirmation.

### Task 3: Verification

- Run focused backend/frontend tests, full Maven tests, Vitest, Vite build, compose rebuild, live API checks, and `git diff --check`.
- Record results in `docs/evidence/auth-rbac-multi-activity-verification.md`.
