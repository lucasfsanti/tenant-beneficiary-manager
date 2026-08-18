# Phase 0 Research: Migrate Database Migrations to Liquibase

## 1. Changelog file format for the two existing migrations

**Decision**: Use Liquibase's "formatted SQL" changelog format — plain `.sql`
files with a `--liquibase formatted sql` header and `--changeset author:id`
comments — for both `001-schema.sql` and `002-seed-demo-data.sql`.

**Rationale**: The existing `V1__schema.sql` and `V2__seed_demo_data.sql` are
already plain, hand-written SQL (DDL + `INSERT`s). Formatted SQL changelogs let
that SQL be reused nearly verbatim (only a changeset header comment is added),
which directly satisfies FR-002 (reproduce the schema/data exactly) with minimal
transcription risk. It also keeps migrations reviewable by anyone who knows SQL,
consistent with Constitution Principle V (avoid unneeded abstraction/tooling
surface).

**Alternatives considered**:
- *Liquibase XML/YAML/JSON changesets* (declarative `createTable`/`insert`
  elements): more portable across database engines, but this project is
  PostgreSQL-only (Constitution: "Technology Stack & Persistence" fixes
  PostgreSQL), so cross-engine portability has no value here, and hand-porting
  `CHECK` constraints, `now()` defaults, and multi-row `INSERT`s into the
  declarative dialect is pure risk for zero benefit.
- *`sqlFile` change type inside a YAML/XML master changelog, pointing at
  untouched `.sql` files*: viable, but adds an extra file per migration
  (changelog wrapper + sql file) instead of one, versus formatted-SQL's single
  file. Rejected only on this project's scale (2 migrations); formatted SQL is
  simpler here.

## 2. Master changelog format and location

**Decision**: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`,
using `include` entries (in order) to pull in `001-schema.sql` and
`002-seed-demo-data.sql` from the same directory.

**Rationale**: `db/changelog/db.changelog-master.yaml` is Spring Boot's
documented default Liquibase changelog location and filename
(`spring.liquibase.change-log` defaults to
`classpath:/db/changelog/db.changelog-master.yaml`), so no extra configuration
key is needed beyond enabling Liquibase — mirroring how `spring.flyway.locations`
today just points at the conventional `db/migration` folder. YAML is used for the
master file (structure only, no embedded SQL) for readability; the actual SQL
stays in the formatted-SQL files from Decision 1.

**Alternatives considered**: XML master changelog — functionally equivalent,
but YAML is less verbose for a two-entry include list and is Liquibase's
commonly recommended default alongside Spring Boot.

## 3. Spring Boot / Maven integration

**Decision**: Replace the two Flyway dependencies in `backend/pom.xml`
(`org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`) with a
single `org.liquibase:liquibase-core` dependency (version managed by the
`spring-boot-starter-parent` 3.3.4 BOM already in use, so no explicit `<version>`
is pinned). In `application.yml`, remove the `spring.flyway` block and add:

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

**Rationale**: Spring Boot auto-configures Liquibase
(`LiquibaseAutoConfiguration`) the moment `liquibase-core` is on the classpath,
the same "add dependency, get auto-config" pattern already used for Flyway — no
extra wiring code. `change-log` is stated explicitly (rather than relying purely
on the default path) to keep the configuration self-documenting, matching the
existing `flyway.locations` line it replaces.

**Alternatives considered**: Programmatic `SpringLiquibase` bean configuration —
unnecessary; the declarative property-based approach is strictly simpler and is
what Constitution Principle V calls for (no speculative configuration knobs).

## 4. Change-tracking / idempotency on restart (FR-004)

**Decision**: Rely on Liquibase's built-in `DATABASECHANGELOG` and
`DATABASECHANGELOGLOCK` tables, created and managed automatically — no custom
tracking needed.

**Rationale**: This is the direct Liquibase analogue of Flyway's
`flyway_schema_history` table the project already depends on implicitly today.
Liquibase records each changeset's id/author/file path and a checksum; on every
startup it compares the changelog against this table and applies only unapplied
changesets, which is exactly the "don't re-apply, don't fail on restart"
behavior FR-004 and acceptance scenario 3 (User Story 1) require. This is
default behavior, not a decision requiring extra configuration.

## 5. Fail-fast on a bad migration (FR-005)

**Decision**: No extra configuration — Liquibase's default behavior already
satisfies this. If a changeset fails to apply, or an already-applied
changeset's checksum no longer matches its file, Liquibase throws and Spring
Boot's `ApplicationContext` fails to start (the same fail-fast shape Flyway
currently provides).

**Rationale**: Matches FR-005 ("fail startup clearly ... rather than starting in
a partially-migrated ... state") with zero additional code, consistent with
Constitution Principle V's simplicity mandate.

## 6. Existing local database volumes (clean-slate cutover)

**Decision**: Document, in the quickstart, that anyone with a pre-existing local
`docker-compose` database volume created under the old Flyway-managed schema
must remove it once (`docker-compose down -v`) before starting the
Liquibase-managed backend, since that volume's `flyway_schema_history` table has
no Liquibase equivalent and the underlying tables already exist. This is a
one-time, one-person action tied to *this specific cutover commit*, not a
recurring manual step in the normal `docker-compose up` flow — it does not
reintroduce a manual migration step per spec Assumption 1 (clean-slate cutover,
no production data at stake).

**Rationale**: Confirmed via `docker-compose.yml`: the `db` service uses a named
volume (`db-data`) that persists across `docker-compose down`/`up` cycles, so a
developer's local Postgres data outlives container recreation. Without this
one-time reset, Liquibase would try to run `CREATE TABLE` against tables Flyway
already created and fail loudly (per Decision 5) — which is correct, safe
behavior, just one the quickstart needs to call out so it isn't mistaken for a
regression.

**Alternatives considered**: Writing a Liquibase changelog that detects and
imports Flyway's history (e.g., `changelogSync` tricks or `preConditions`
checking for existing tables) — rejected as unnecessary complexity for a project
with no production deployment and no data worth preserving across the cutover
(spec Assumption 1); it would add real complexity to work around a one-time
local dev inconvenience.

## 7. Testcontainers-based tests

**Decision**: No test-specific changes required. `backend/src/test` has no
Flyway-specific configuration or resource files (confirmed by search), so the
existing Testcontainers PostgreSQL setup will pick up the new Liquibase
changelog through the same Spring context auto-configuration used in production,
with no separate `application-test.yml` migration override to update.

**Rationale**: Verified there are no `flyway`/`migration`/`liquibase` references
anywhere under `backend/src/test`. Tests boot the full Spring context against a
real Testcontainers Postgres instance, so swapping the migration tool in
`application.yml` is sufficient — the same file both production and tests read.
