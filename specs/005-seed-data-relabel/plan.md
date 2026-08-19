# Implementation Plan: Clearer, Expanded Demo Seed Data

**Branch**: `005-seed-data-relabel` | **Date**: 2026-08-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-seed-data-relabel/spec.md`

## Summary

Three changes, delivered together: (1) relabel every existing seeded Tenant/Pessoa/User/
Beneficiário with a systematic, self-describing name ("Tenant 1", "User 1 - ADMIN", "Beneficiário
1 - Tenant 1") without changing any record's id or any user's real tenant memberships/admin
standing; (2) add new Tenants/Users/Pessoas/Beneficiários to broaden the demo roster; (3) make
demo-data insertion skippable at startup, on by default. Per the Clarifications session, (3) is
delivered by **retroactively splitting** the one existing changeset that mixes a schema change
with demo-data seeding (`003-role-system.sql`) into a schema-only changeset and a new
data-only changeset — accepting that this requires anyone with an existing local database to
recreate it (`docker compose down -v`) — and by tagging every demo-data changeset (existing and
new) with a Liquibase `context:demo`, gated by a Spring profile (`demo`) that is **active by
default** so `docker-compose up` keeps seeding demo data exactly as it does today, with no new
step. Starting the app with that profile explicitly cleared yields a fully-migrated,
empty-of-demo-data database.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3.4) / SQL (Liquibase 4.27.0 changesets) —
unchanged; no frontend code changes (two Vitest files use similarly-named but fully
self-contained mock data, unrelated to the real seed — confirmed by inspection)

**Primary Dependencies**: No new dependency. Reuses Liquibase's built-in `context` changeset
attribute and Spring Boot's built-in profile mechanism (`spring.profiles.active`,
`spring.liquibase.contexts`) — both already part of the existing `liquibase-core` /
`spring-boot-starter` dependencies, nothing new to add. Reuses the existing
`CpfValidator` check-digit rule for new Pessoa CPFs

**Storage**: PostgreSQL 16, same schema — no new tables/columns, only changelog reorganization
and data. Existing tables: `tenant`, `app_user`, `user_tenant_membership`, `pessoa`, `beneficiario`

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL). Tests never set
`@ActiveProfiles`, so they pick up the new `demo` profile default the same way `docker-compose`
does — demo data keeps seeding for every test run exactly as today, with zero test-infrastructure
change required for the profile/context mechanism itself. 3 files need a literal-value edit for
the renamed data (see data-model.md); 6 more get a doc-comment-only accuracy edit

**Target Platform**: Linux containers via Docker Compose — unchanged

**Project Type**: Backend-only (migration + config + doc + test-literal updates). No frontend
changes

**Performance Goals**: N/A — one-time startup migration, no runtime performance implication

**Constraints**: Renaming MUST NOT change any existing row's id or any user's tenant
memberships/admin standing (spec FR-005); new Pessoa CPFs MUST pass `CpfValidator`'s real
check-digit algorithm; the demo password stays the single shared value already used today (FR-006);
splitting `003-role-system.sql` changes its file checksum, so this is the one accepted
exception to "no manual step" (spec Assumptions) — every migration after this one keeps requiring
zero manual steps, same as before

**Scale/Scope**: 1 edited changeset (`003-role-system.sql`, schema-only after the split) + 3 new
changesets (extracted role-system seed data, plus the relabel/expand data, both `context:demo`;
`002-seed-demo-data.sql` gets a one-line `context:demo` tag added to its existing header — the
only edit to its content); 1 new multi-document block in `application.yml` (the `demo` profile's
Liquibase context); 3 backend test files get a literal-value edit; 6 more get a doc-comment-only
edit; `README.md` gets its demo credentials table updated and a short note on the new toggle.
0 new REST endpoints, 0 schema (table/column) changes, 0 frontend changes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. Multitenant Data Isolation (NON-NEGOTIABLE) | PASS | No isolation-relevant code changes — this is migration/config/data only. Every new/renamed row still respects the same tenant-scoping columns and constraints the schema already enforces. |
| II. Data Integrity & Explicit Validation | PASS | New Pessoa CPFs are real, check-digit-valid values (verified against `CpfValidator`'s algorithm before use). No new error-handling paths. |
| III. API Contract Documentation | PASS | No endpoint, request, or response shape changes — see `contracts/README.md`. |
| IV. Reproducible, Zero-Touch Environment | PASS | The default `docker-compose up` path requires no new step — the `demo` profile (and thus demo-data seeding) is active by default. Splitting `003-role-system.sql` is the one, explicitly-accepted (Clarifications) exception where an existing local database must be recreated once; every migration before and after that point applies automatically with no manual step, matching this principle's spirit even though the one-time exception is a deliberate, documented trade-off rather than a violation. |
| V. Simplicity & Justified Technology Choices | PASS | No new dependency, no new mechanism — Liquibase `context` and Spring `profiles.active` are both stock features of the already-mandated stack, used via a single multi-document block appended to the existing `application.yml` rather than a new file. |
| Technology Stack & Persistence | PASS | Same stack; schema managed exclusively through versioned migrations, as already required. |
| Delivery & Documentation Requirements | PASS | `README.md` is updated with the new demo credentials and a short note on the opt-out toggle, so documentation doesn't drift from what `docker-compose up` actually seeds. |

No violations requiring justification — Complexity Tracking is empty. (The one-time local-database
recreate is a spec-level, user-accepted trade-off recorded in Clarifications, not a constitution
violation: Principle IV's zero-touch guarantee is about *ongoing* startup behavior, which this
plan preserves — the recreate is a single migration event, not a recurring manual step.)

## Project Structure

### Documentation (this feature)

```text
specs/005-seed-data-relabel/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── README.md        # No contract changes — this feature is seed data + config only
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/resources/
│   ├── application.yml                        # + spring.profiles.active: demo (default);
│   │                                           #   + new `---` document: on-profile "demo" sets
│   │                                           #   spring.liquibase.contexts: demo
│   └── db/changelog/
│       ├── db.changelog-master.yaml            # + includes for the 2 new changeset files, in order
│       ├── 002-seed-demo-data.sql              # EDITED: header gets `context:demo` only —
│       │                                       #   INSERT content unchanged (checksum changes,
│       │                                       #   accepted per Clarifications)
│       ├── 003-role-system.sql                 # EDITED: schema-only now — the ALTER TABLE
│       │                                       #   statements stay; the seed INSERT/UPDATE
│       │                                       #   statements are removed (moved to 004)
│       ├── 004-role-system-seed-data.sql       # NEW: the admin-user INSERT + bruno's
│       │                                       #   is_tenant_admin UPDATE extracted from the old
│       │                                       #   003, tagged context:demo
│       └── 005-seed-data-relabel-and-expand.sql # NEW: UPDATEs renaming existing rows +
│                                                #   INSERTs for new Tenants/Users/Pessoas/
│                                                #   Beneficiários, tagged context:demo
└── src/test/java/com/tbm/integration/
    ├── TenantUpdateAuthorizationTest.java      # literal "Tenant Alfa" (x2) -> "Tenant 1"
    ├── PessoaDeletionRestrictionTest.java      # anti-leak substrings "tenant alfa"/"tenant beta"
    │                                           #   -> "tenant 1"/"tenant 2"; doc comment
    ├── TenantIsolationTest.java                # literal matricula "MAT-B-001" -> "Beneficiário
    │                                           #   1 - Tenant 2"; doc comment
    ├── AbstractIntegrationTest.java             # doc comments only (constant names/ids unchanged)
    ├── TenantCrudTest.java                      # doc comment only
    ├── TenantAdminGrantRevokeTest.java          # doc comments only (x2)
    ├── MembershipManagementTest.java            # doc comment only
    ├── BeneficiarioCreationTest.java            # doc comment only
    └── TenantMembershipEnforcementTest.java     # doc comment only

README.md   # "Usuários de demonstração" table updated; + short note on the demo-data toggle

frontend/   # untouched
```

**Structure Decision**: Same `backend/`/`frontend/` layout as `001`–`004`; no new top-level
directories. Seed data stays in the existing `db/changelog/` directory following the
established numbering; the profile/context wiring extends the existing single `application.yml`
via a multi-document block rather than introducing a new `application-demo.yml` file, keeping
configuration in one place.

## Post-Design Constitution Check

*Re-evaluated after Phase 1 (data-model.md, contracts/README.md, quickstart.md).*

All seven rows of the Constitution Check table above still PASS with no changes:
`data-model.md`'s relationship table confirms every existing user's tenant memberships/admin
standing survive the rename unchanged (Principle I); `research.md §4`'s CPF computation is
verified against the real `CpfValidator` algorithm, not a cosmetic pattern (Principle II);
`contracts/README.md` confirms zero contract drift (Principle III); `quickstart.md` §1/§4
demonstrate the default path stays zero-touch while the opt-out is a single explicit environment
override (Principle IV); no new dependency was introduced during design (Principle V). No new
complexity or deviation was introduced during design.

## Complexity Tracking

*No violations — table not needed.*
