# Feature Specification: Migrate Database Migrations to Liquibase

**Feature Branch**: `002-liquibase-migration`

**Created**: 2026-08-17

**Status**: Draft

**Input**: User description: "Change the database migration tool to Liquibase"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Zero-touch startup still works after the tooling swap (Priority: P1)

A developer or reviewer clones the repository and runs the project's single startup
command. The database schema and seed data must be created automatically, with no
manual migration step, exactly as they are today — only the tool doing the work
changes.

**Why this priority**: This is the core deliverable. If startup stops being
zero-touch, the change breaks an existing, non-negotiable delivery requirement
(reproducible environment) for no benefit.

**Independent Test**: On a clean checkout with no pre-existing database volume, run
the project's standard startup command and verify the database ends up with the
same tables, columns, constraints, and seed rows as before the change, without
running any command beyond the standard startup.

**Acceptance Scenarios**:

1. **Given** a clean environment with no existing database volume, **When** the
   stack is started, **Then** the schema is created automatically and matches the
   schema previously produced by the replaced tool.
2. **Given** a clean environment with no existing database volume, **When** the
   stack is started, **Then** the previously defined seed/demo data is present in
   the database without any manual step.
3. **Given** the stack has already been started once, **When** it is restarted
   without any schema change, **Then** startup does not fail or re-apply changes
   that were already applied.
4. **Given** the stack is already running with the initial set of changes
   applied, **When** a new, additional change is introduced and the stack is
   restarted, **Then** only the new change is applied — the previously-applied
   changes are not re-applied — and startup succeeds.

---

### User Story 2 - Old migration tooling is fully removed (Priority: P2)

A developer inspecting the codebase (dependencies, configuration, and migration
files) finds only the new migration tool in use. No leftover references to the
replaced tool remain to cause confusion or accidental drift between two migration
mechanisms.

**Why this priority**: Leaving the old tool partially in place (as a dependency,
config block, or set of orphaned files) creates ambiguity about which tool is
actually authoritative for the schema, and risks both tools trying to manage the
same database.

**Independent Test**: Search the codebase and dependency manifest for the replaced
tool's package, configuration keys, and migration file conventions; confirm none
remain, and confirm the project still builds and starts successfully.

**Acceptance Scenarios**:

1. **Given** the migration tool has been changed, **When** the backend's dependency
   manifest is inspected, **Then** it declares only the new migration tool, not the
   replaced one.
2. **Given** the migration tool has been changed, **When** the backend's
   configuration is inspected, **Then** it configures only the new migration tool.
3. **Given** the migration tool has been changed, **When** the repository's
   migration files are inspected, **Then** only the new tool's changelog format is
   present.

---

### User Story 3 - Documentation reflects the current migration tool (Priority: P3)

A reviewer reading the project's documentation sees the correct, current migration
tool named wherever the schema-management approach is described, so the docs never
contradict the actual running system.

**Why this priority**: Lower priority than the functional swap itself, but stale
documentation actively misleads reviewers evaluating the architectural decisions,
which the project's documentation is explicitly required to describe accurately.

**Independent Test**: Search project documentation for mentions of the previous
migration tool's name and confirm they have been updated or removed.

**Acceptance Scenarios**:

1. **Given** the documentation describes how the database schema is managed,
   **When** it is read after the change, **Then** it names the new migration tool,
   not the replaced one.
2. **Given** the schema-management approach is described in more than one
   documentation location, **When** the change is applied, **Then** every such
   location is updated consistently — no location is left naming the old tool
   while another names the new one.

---

### Edge Cases

- What happens when the stack is started against a database volume that still has
  schema-version tracking data from the previous migration tool? (Assumed
  clean-slate: see Assumptions.)
- What happens if a new schema change needs to be added in the future — does the
  changelog structure support incremental, ordered additions the same way the
  previous tool's numbered files did? (Resolved: see FR-004 — new changes are
  always appended as additional ordered entries after all existing ones, never
  inserted before or renumbered.)
- What happens if the new tool fails to apply a change on startup — does the stack
  fail loudly and refuse to start, rather than starting in a partially-migrated
  state?
- What happens when an already-applied changelog entry's content is modified
  after the fact (e.g., a local fix-up edit)? (Expected: treated as a migration
  failure per FR-005 — startup fails clearly rather than silently accepting the
  drifted content.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST manage all database schema and seed-data changes
  exclusively through the new migration tool; no schema or seed change may be
  applied by any other mechanism (e.g., ORM auto-DDL or hand-run scripts).
- **FR-002**: The new migration tool's changelog(s) MUST reproduce, exactly, the
  schema (tables, columns, constraints, indexes) and seed data currently produced
  by the existing migration files. "Exactly" means identical row values for every
  column, except for columns whose value is generated at insert time by the
  database itself (e.g., a `now()`-based timestamp default) — those MUST remain
  populated by the same server-side generation mechanism, not a literal copied
  value.
- **FR-003**: Schema and seed-data changes MUST be applied automatically when the
  backend starts, with no manual step required beyond the project's standard
  startup command.
- **FR-004**: The system MUST track which changes have already been applied, so
  that restarting the stack does not re-apply already-applied changes or fail due
  to duplicate application. Future schema/seed changes MUST be introduced as new,
  additional changelog entries appended after all existing ones — never by
  editing or renumbering an already-applied entry — so this ordering guarantee
  remains stable as the changelog grows over time.
- **FR-005**: The system MUST fail startup clearly if a migration cannot be
  applied, rather than starting in a partially-migrated or silently inconsistent
  state. "Clearly" means the application process exits with a non-zero status
  and the failure is visible in startup logs, rather than starting up and
  reporting itself healthy.
- **FR-006**: All dependencies, configuration, and migration files belonging to
  the previous migration tool MUST be removed from the codebase, including any
  compiled or otherwise generated copies of those migration files (e.g., build
  output directories) — not source files alone.
- **FR-007**: Project documentation that describes how the database schema is
  managed MUST be updated to name the new migration tool.

### Key Entities

- **Migration Changelog**: An ordered, versioned definition of a schema or seed
  data change (create table, add column, insert seed rows, etc.), replacing the
  previous tool's numbered SQL migration files.
- **Applied-Change History**: A persistent record, inside the database itself, of
  which changes have already been applied and in what order — used to guarantee
  changes are applied exactly once and in sequence.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Starting the project from a clean checkout with a single standard
  command results in a fully initialized, correctly seeded database with zero
  manual steps, 100% of the time.
- **SC-002**: 100% of the schema objects and seed rows present before the tooling
  change are present and identical after the change.
- **SC-003**: A search of the codebase for the previous migration tool's
  dependency, configuration, and file-naming conventions returns zero results.
  This search scope is the current working tree (source files and build output),
  not version-control history — pre-existing commits that reference the previous
  tool are expected and out of scope.
- **SC-004**: Restarting an already-migrated stack completes without error and
  without re-applying any previously-applied change, 100% of the time.

## Assumptions

- The project currently has no deployed environment with production data at
  stake; any existing local database volumes were created for development/demo
  purposes and can be recreated from scratch (clean-slate cutover). No
  data-preserving upgrade path from the old tool's version-tracking table is
  required.
- The exact internal format of the new tool's changelog files (e.g., SQL, XML, or
  YAML) is an implementation detail left to the planning phase, as long as it
  satisfies the ordering, idempotency, and auto-apply-on-startup requirements
  above.
- The database engine, connection configuration, and application startup command
  used today remain unchanged — only the mechanism that applies schema changes is
  being replaced.
- Only a single backend instance applies migrations at a time in this project's
  deployment (the standard `docker-compose up` runs one backend replica);
  concurrent/multi-instance migration application is out of scope for this
  feature.
- Schema/seed application at startup is expected to complete well within the
  existing backend health-check's `start_period` (`docker-compose.yml`,
  currently 30s), given the small scope of this migration (2 changesets, ~17
  seed rows). This feature does not change, and does not need to change, that
  health-check budget.
- The demo seed data's password hashes are non-sensitive, publicly-known demo
  credentials already committed in the current migration files; moving them into
  the new tool's changelog does not change their exposure level or introduce a
  new secrets-handling concern.
