# Implementation Plan: Migrate Database Migrations to Liquibase

**Branch**: `002-liquibase-migration` | **Date**: 2026-08-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-liquibase-migration/spec.md`

## Summary

Replace Flyway with Liquibase as the sole mechanism for managing the PostgreSQL
schema and demo seed data. The existing `V1__schema.sql` (5 tables + 1 index) and
`V2__seed_demo_data.sql` (demo tenants, users, memberships, pessoas,
beneficiarios) are re-expressed as two Liquibase changesets applied automatically
by Spring Boot at backend startup, preserving the current zero-touch
`docker-compose up` behavior exactly. All Flyway dependencies, configuration, and
files are removed; the README line referencing Flyway is updated.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.3.4

**Primary Dependencies**: `liquibase-core` (replaces `flyway-core` /
`flyway-database-postgresql`), Spring Boot's built-in Liquibase auto-configuration
(`spring-boot-starter-data-jpa` already present; Liquibase integration ships via
`org.liquibase:liquibase-core` on the classpath, auto-wired by
`spring-boot-autoconfigure`)

**Storage**: PostgreSQL 16 (via `docker-compose.yml` service `db`; unchanged)

**Testing**: `mvn test` — JUnit 5 + Testcontainers PostgreSQL (`org.testcontainers:postgresql`,
`org.testcontainers:junit-jupiter`); the Spring context used in tests boots against a
real Postgres container and will exercise the Liquibase changelog the same way
production startup does, since no test resource currently overrides the migration
tool

**Target Platform**: Linux server (Docker container, `docker-compose.yml`)

**Project Type**: Web application (`backend/` Spring Boot API + `frontend/` Vue 3), this
feature touches `backend/` only

**Performance Goals**: N/A — startup-time migration only; no new runtime request
path. Migration application (2 changesets, ~17 rows) is expected to complete
well within the backend healthcheck's existing 30s `start_period`
(`docker-compose.yml`); this feature does not need to change that budget (see
spec.md Assumptions).

**Constraints**: Must preserve `docker-compose up` as the single zero-touch startup
command (Constitution Principle IV); schema/data output must reproduce what
Flyway currently produces exactly, per FR-002 — except server-generated columns
(e.g., `now()`-based timestamp defaults), which must remain server-generated
rather than literal copied values

**Scale/Scope**: 2 migration files → 2 Liquibase changesets; 5 tables, 1 index, ~17
seed rows total (2 tenants, 2 users, 3 memberships, 4 pessoas, 4 beneficiarios).
Single backend instance applies migrations (standard `docker-compose up`);
concurrent/multi-instance migration application is out of scope (see spec.md
Assumptions).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle IV (Reproducible, Zero-Touch Environment)**: PASS. Principle IV
  explicitly names "Flyway or Liquibase" as acceptable, applied at boot. This
  feature keeps schema changes as versioned migrations, applied automatically on
  `docker-compose up`, with no manual step — no gate violation, straight
  tool-for-tool substitution within an already-permitted option.
- **Principle V (Simplicity & Justified Technology Choices)**: PASS. Liquibase is
  one of the two tools the constitution names explicitly for this exact purpose
  (see "Technology Stack & Persistence" section), so no deviation justification
  is required.
- **Principle I (Multitenant Data Isolation)**: N/A. This feature does not touch
  query/mutation paths or tenant-scoping logic — schema shape and seed data are
  reproduced exactly, so isolation guarantees are unaffected.
- **Principle II (Data Integrity & Explicit Validation)**: N/A. No business-rule
  or API validation logic changes; DB-level constraints (`UNIQUE`, `CHECK`,
  foreign keys) are carried over unchanged into the new changelog.
- **Principle III (API Contract Documentation)**: N/A. No REST endpoint or
  contract changes.

No violations. Complexity Tracking table is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-liquibase-migration/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory: this feature has no REST/API surface of its own (see
Phase 1, "Define interface contracts" — skipped, purely internal schema-tooling
change).

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/...                          # unchanged
│   │   └── resources/
│   │       ├── application.yml                # flyway: block removed, liquibase: block added
│   │       └── db/
│   │           ├── migration/                  # REMOVED (Flyway convention)
│   │           │   ├── V1__schema.sql
│   │           │   └── V2__seed_demo_data.sql
│   │           └── changelog/                  # NEW (Liquibase convention)
│   │               ├── db.changelog-master.yaml
│   │               ├── 001-schema.sql
│   │               └── 002-seed-demo-data.sql
│   └── test/                                   # unchanged; Testcontainers-backed
│       └── ...
├── pom.xml                                     # flyway-core / flyway-database-postgresql
│                                                # removed; liquibase-core added
└── target/                                     # build output, not touched directly

frontend/                                       # untouched by this feature

README.md                                       # "As migrações Flyway" line updated
                                                 # to name Liquibase
```

**Structure Decision**: Existing single-backend / single-frontend layout
(`backend/`, `frontend/`) is unchanged. This feature is scoped entirely to
`backend/src/main/resources/` (migration files + config) and `backend/pom.xml`
(dependency swap), plus a one-line `README.md` correction. No new modules,
services, or directories beyond the new `db/changelog/` folder replacing
`db/migration/`.

## Complexity Tracking

*No violations — table not needed.*
