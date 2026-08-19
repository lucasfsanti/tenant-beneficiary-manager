# Implementation Plan: User Self-Registration (Bootstrap Entrypoint)

**Branch**: `006-user-self-registration` | **Date**: 2026-08-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-user-self-registration/spec.md`

## Summary

Adds a public, unauthenticated "Create User" page and a matching backend endpoint. The very
first account ever created (while `app_user` is empty) is automatically granted System Admin,
with no role choice presented; every account created afterward is automatically Normal, with no
Tenant membership and no role choice — client input never influences which role is granted
(spec FR-011). This closes the gap left open by feature 005: with demo-data seeding turned off,
or on a brand-new production database, there was previously no way for anyone to ever obtain a
login. The page stays permanently reachable (not a one-time wizard) because it is the platform's
only account-creation path — existing admin capabilities can only grant/revoke standing on
accounts that already exist, never create a new one. Backend test coverage spans two new
integration test files plus new cases added to the existing `AuthServiceTest.java` unit test,
because the existing shared Testcontainers container is always demo-seeded before any test runs
and so cannot represent a genuinely empty platform — see Project Structure below and tasks.md's
"Testing architecture note" for the full rationale. Implementation also surfaced two real bugs
beyond what was originally planned — a missing `SecurityConfig.java` `permitAll()` entry (the
new endpoint 401'd without it) and a PostgreSQL snapshot-timing bug in the advisory-lock check
that let two concurrent registrations both become System Admin — both found by the tests written
for this feature and fixed during implementation; see Project Structure below and tasks.md T009/
T015 for the full account.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3.4) / Vue 3 (frontend, Composition API +
Vite) — unchanged, no new language/runtime

**Primary Dependencies**: No new dependency. Reuses the existing `PasswordEncoder` (BCrypt),
Spring Data JPA, the existing JWT-issuing `JwtService` (only indirectly — registration does not
itself issue a token, per spec Assumptions/FR-009), Vue Router's existing `meta: { public: true }`
route pattern, and the existing Pinia `auth` store's action/error-handling shape. One new but
native technique: a PostgreSQL session/transaction advisory lock (`pg_advisory_xact_lock`), used
to make the "is this the very first account?" check-then-insert atomic under concurrent requests
(research.md §1) — no library, just a native SQL function already available in the already-used
PostgreSQL 16 image

**Storage**: PostgreSQL 16, same schema — no new tables/columns. `app_user.is_system_admin`
(added in feature 003) and an empty `user_tenant_membership` row set already fully express "System
Admin with no memberships" and "Normal with no memberships"; no migration is needed for this
feature (confirmed in research.md §2)

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) for the backend, including a
concurrency test exercising the race condition from spec Edge Cases; Vitest for the new frontend
view/store, matching the existing `LoginView`/`auth.js` test conventions

**Target Platform**: Linux containers via Docker Compose — unchanged

**Project Type**: Web application (backend + frontend) — same as every prior feature in this repo

**Performance Goals**: N/A — an occasional, low-volume action (account creation), not a hot path

**Constraints**: Under concurrent submissions while the platform is empty, at most one account may
ever receive System Admin through the "platform is empty" path (spec Edge Cases, FR-011); the
granted role MUST be decided entirely server-side — nothing in the client's request may influence
it (spec FR-011); no change to the existing tenant-isolation enforcement path, since new accounts
start with zero Tenant memberships regardless of which role they receive

**Scale/Scope**: 1 new public backend endpoint, 1 new request DTO, 2 new repository methods
implementing the advisory-lock-guarded role decision, 1 existing file (`SecurityConfig.java`)
edited to expose the new endpoint publicly, 0 schema changes, 1 new frontend view, 1 new public
route, 1 new store action, a short README update, 2 new backend integration test files plus new
cases in the existing `AuthServiceTest.java` unit test (see Project Structure below)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
| --- | --- | --- |
| I. Multitenant Data Isolation (NON-NEGOTIABLE) | PASS | This feature only creates `app_user` rows with zero `user_tenant_membership` rows — it never touches Beneficiário/Pessoa access paths or `TenantContextFilter`. No isolation-relevant code changes. |
| II. Data Integrity & Explicit Validation | PASS | Username uniqueness is enforced server-side (existing DB `UNIQUE` constraint plus an explicit pre-check mapped to a `409 Conflict` RFC 7807 response, mirroring the existing `ConflictException` pattern); non-blank validation via `@NotBlank`, mirroring `LoginRequest`. The role-granting decision itself is a business rule enforced entirely server-side, deliberately ignoring any client-supplied signal (spec FR-011) — the sharpest-possible application of "the API is the actual trust boundary." |
| III. API Contract Documentation | PASS | The new endpoint is documented via the same springdoc/Swagger annotations already used on `AuthController` (`@Tag`, generated OpenAPI) — see `contracts/README.md`. No hand-maintained spec. |
| IV. Reproducible, Zero-Touch Environment | PASS | No new migration, so no database recreate is needed for this feature. `docker compose up` remains a single command with no manual step, and this feature works identically whether feature 005's demo-data toggle is on or off — it's what makes the "off" mode (previously a dead-end per feature 005's Assumptions) actually usable end to end. |
| V. Simplicity & Justified Technology Choices | PASS | No new dependency or library. The one new technique — a PostgreSQL advisory lock — is a native, already-available database primitive, justified in research.md §1 as the narrowest fix for a specific, real concurrency requirement (spec Edge Cases), not a speculative abstraction. |
| Technology Stack & Persistence | PASS | Same stack; schema unchanged, still managed exclusively through versioned migrations (none needed here). |
| Delivery & Documentation Requirements | PASS | `README.md` gets a short note documenting the new entrypoint, superseding feature 005's "no account-creation path outside of demo-data seeding" caveat. |

No violations requiring justification — Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/006-user-self-registration/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── README.md        # New endpoint's contract description
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/tbm/user/
│   ├── AuthController.java          # + new public POST /api/auth/register endpoint, alongside
│   │                                 #   the existing /api/auth/login (research.md §3)
│   ├── AuthService.java             # + register(username, password): validates uniqueness,
│   │                                 #   decides the role via the advisory-lock-guarded emptiness
│   │                                 #   check, persists the new AppUser (spec FR-001–003,
│   │                                 #   FR-005, FR-011)
│   ├── AppUserRepository.java       # + two native-query methods:
│   │                                 #   acquireFirstAccountDecisionLock() (takes the advisory
│   │                                 #   lock) and anyAccountExists() (the existence check) —
│   │                                 #   deliberately separate statements, not one combined
│   │                                 #   query, per the concurrency bug found and fixed during
│   │                                 #   T015 (research.md §1)
│   └── dto/
│       └── RegisterRequest.java     # NEW: username + password, @NotBlank (mirrors
│                                     #   LoginRequest.java)
├── src/main/java/com/tbm/config/
│   └── SecurityConfig.java          # + "/api/auth/register" added to the permitAll() matcher —
│                                     #   found and fixed during T009: @SecurityRequirement is
│                                     #   Swagger-only documentation and does nothing to actually
│                                     #   exempt a route from Spring Security's authorization
│                                     #   rules; the endpoint 401'd until this was added
└── src/test/java/com/tbm/
    ├── integration/
    │   ├── UserSelfRegistrationTest.java # NEW: extends AbstractIntegrationTest (shared,
    │   │                                 #   always-demo-seeded container) — covers registering
    │   │                                 #   against an already-populated platform, duplicate-
    │   │                                 #   username rejection, blank-field validation, and
    │   │                                 #   client-supplied-role-is-ignored (tasks.md T011, T014)
    │   └── UserSelfRegistrationBootstrapTest.java # NEW: does NOT extend AbstractIntegrationTest
    │                                     #   — starts its own isolated Testcontainers Postgres
    │                                     #   with the no-demo profile, since the shared container
    │                                     #   is never genuinely empty by the time any test runs.
    │                                     #   Covers first-account-becomes-admin and the
    │                                     #   concurrent-registration race (tasks.md T009, T015)
    └── unit/
        └── AuthServiceTest.java # EDITED (pre-existing file, not new): T010 added 3 new
                                  # @Test methods here — registerGrantsSystemAdminWhenNo...,
                                  # registerGrantsNormalWhenAn..., registerRejectsADuplicate...
                                  # — reusing this file's existing Mockito-mock-based
                                  # AuthService construction instead of creating a new file

frontend/
├── src/views/
│   └── CreateUserView.vue           # NEW: mirrors LoginView.vue's structure/style; no role
│                                     #   picker is rendered (spec FR-003/FR-005 — there is
│                                     #   nothing to choose)
├── src/router/index.js              # + new public route (meta: { public: true }, matching
│                                     #   the existing /login route's pattern)
└── src/stores/auth.js               # + register(username, password) action mirroring login()'s
                                       #   shape/error-handling, without establishing a session
                                       #   (spec FR-009/Assumptions — redirects to /login instead)

README.md   # + short note documenting the new entrypoint; supersedes feature 005's "no
            #   account-creation path" caveat
```

**Structure Decision**: Same `backend/`/`frontend/` layout as every prior feature; no new
top-level directories. The new endpoint lives in the existing `AuthController`/`AuthService`
pair (registration is conceptually part of "how someone gets into the system," the same concern
`AuthController` already owns) rather than a new controller — see research.md §3 for the
alternatives considered.

## Post-Design Constitution Check

*Re-evaluated after Phase 1 (data-model.md, contracts/README.md, quickstart.md).*

All seven rows of the Constitution Check table above still PASS with no changes:
`data-model.md` confirms no schema change is needed and that "Normal with zero memberships" is
already a representable, unconstrained state (Principle I — no isolation-relevant change at all);
`contracts/README.md` shows the new endpoint has no role field in its request body, closing off
the client-influence path FR-011 forbids, and reuses the existing RFC 7807 error shapes for both
failure cases (Principle II/III); `quickstart.md` §5 gives a directly runnable check for the one
genuinely new piece of behavior (the concurrency guarantee from research.md §1), and §1–4 confirm
the default `docker compose up` path is untouched (Principle IV). No new dependency was introduced
during design (Principle V). No new complexity or deviation was introduced during design.

## Complexity Tracking

*No violations — table not needed.*
