# Phase 1 Data Model: Migrate Database Migrations to Liquibase

This feature does not introduce or change any business entity. `tenant`,
`app_user`, `user_tenant_membership`, `pessoa`, and `beneficiario` (defined today
in `V1__schema.sql`/`V2__seed_demo_data.sql`) are carried over with identical
columns, constraints, and seed rows — see Decision 1 in `research.md`. The
entities below are the two migration-tooling concepts introduced by the spec's
"Key Entities" section.

## Migration Changelog

Represents one ordered, versioned unit of schema/data change, replacing a
Flyway `V{n}__description.sql` file.

| Field | Description |
|---|---|
| `id` | Changeset identifier, unique within its file (e.g., `1`, `2`) — carried in the `--changeset author:id` header |
| `author` | Fixed value `tbm` for both changesets in this migration |
| `file path` | Location within `db/changelog/` (e.g., `001-schema.sql`) |
| `checksum` | Computed by Liquibase from file content; used to detect drift on already-applied changesets |
| `body` | The DDL/DML statements to execute (unchanged SQL from the Flyway files) |

**Instances for this feature** (in apply order, matching the existing V1 → V2
order):

1. `001-schema.sql` — changeset `tbm:1` — creates `tenant`, `app_user`,
   `user_tenant_membership`, `pessoa`, `beneficiario`, and
   `idx_beneficiario_tenant_pessoa`. Content is `V1__schema.sql` unchanged, with
   a formatted-SQL changeset header added.
2. `002-seed-demo-data.sql` — changeset `tbm:2` — inserts the 2 demo tenants, 2
   demo users, 3 memberships, 4 pessoas, 4 beneficiarios. Content is
   `V2__seed_demo_data.sql` unchanged, with a formatted-SQL changeset header
   added.

**Validation rules** (from FR-002, FR-004, FR-005):
- Each changeset's `id`+`author`+file combination MUST be unique within the
  changelog (Liquibase enforces this).
- Changeset order in `db.changelog-master.yaml`'s `include` list MUST match the
  existing V1 → V2 dependency order (schema before seed data, since seed
  `INSERT`s reference tables created in changeset 1 and rows reference each
  other via foreign keys within changeset 2).
- A changeset, once applied, MUST NOT be edited in place — its checksum would no
  longer match and Liquibase would refuse to start (this mirrors Flyway's same
  rule for already-applied `V{n}` files and is why future schema changes must be
  added as new changesets, not edits to `001`/`002` — see spec.md FR-004,
  Edge Cases, and User Story 1 acceptance scenario 4).

## Applied-Change History

Represents the persistent, in-database record of which changesets have already
run — the direct analogue of Flyway's `flyway_schema_history` table, and the
mechanism satisfying FR-004 (no re-application on restart).

| Field | Description |
|---|---|
| `DATABASECHANGELOG` (table) | One row per applied changeset: id, author, filename, checksum (`MD5SUM`), execution timestamp, order executed |
| `DATABASECHANGELOGLOCK` (table) | Single-row lock table preventing two concurrent Liquibase runs (e.g., if the backend were ever scaled to multiple replicas starting simultaneously) |

Both tables are created and managed automatically by Liquibase on first run —
no manual DDL, no application code interacts with them directly. They exist for
the lifetime of the database volume, same as `flyway_schema_history` does today.

**State transitions**: A changeset moves from *pending* (present in the
changelog, absent from `DATABASECHANGELOG`) to *applied* (row present) exactly
once, on the first successful startup that includes it. There is no supported
transition back to *pending* short of manually deleting its
`DATABASECHANGELOG` row and its schema effects — not a workflow this feature
introduces or needs to support (see research.md Decision 6 for the one-time
clean-slate cutover instead).
