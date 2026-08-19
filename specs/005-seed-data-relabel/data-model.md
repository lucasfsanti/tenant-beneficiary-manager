# Data Model: Clearer, Expanded Demo Seed Data

## Persisted data

No schema (table/column) change. This feature only touches rows in already-existing tables:
`tenant`, `app_user`, `user_tenant_membership`, `pessoa`, `beneficiario`. See research.md §3/§4
for the exact rename mapping and new rows.

## Migration/changelog model (new for this feature)

| Construct | Kind | Scope |
|---|---|---|
| Liquibase `context:demo` changeset attribute | metadata on a changeset | Applied to `002-seed-demo-data.sql`, `004-role-system-seed-data.sql`, `005-seed-data-relabel-and-expand.sql` — every changeset that inserts/updates demo data, and only those |
| `spring.liquibase.contexts: demo` | Spring property, active only under the `demo` profile | Determines whether `context:demo` changesets run at all |
| `spring.profiles.active: demo` | Spring property, set as the base `application.yml` default | Makes the `demo` profile — and therefore demo-data seeding — the default outcome of starting the app with no override; an external `SPRING_PROFILES_ACTIVE` overrides it |

## Protected invariant: relationships preserved across the rename (spec FR-005)

Every existing app_user's tenant memberships and admin standing are unchanged by the rename —
only the `username`/`nome`/`matricula` display fields change:

| User (new name) | System Admin? | Tenant Admin of | Member of |
|---|---|---|---|
| User 1 - NORMAL (was ana) | No | — | Tenant 1, Tenant 2 |
| User 2 - TENANT ADMIN (was bruno) | No | Tenant 1 | Tenant 1 |
| User 3 - ADMIN (was admin) | Yes | — | (none — System Admin standing is platform-wide) |

New users added by this feature:

| User (new) | System Admin? | Tenant Admin of | Member of |
|---|---|---|---|
| User 4 - TENANT ADMIN | No | Tenant 3 | Tenant 3 |
| User 5 - NORMAL | No | — | Tenant 3, Tenant 4 |
| User 6 - NORMAL | No | — | Tenant 2 |

## Beneficiário coverage (spec FR-010: every tenant has ≥1)

| Tenant | Beneficiário(s) |
|---|---|
| Tenant 1 | Beneficiário 1 - Tenant 1 (Pessoa 1), Beneficiário 2 - Tenant 1 (Pessoa 2) |
| Tenant 2 | Beneficiário 1 - Tenant 2 (Pessoa 3), Beneficiário 2 - Tenant 2 (Pessoa 1 — reused across tenants, mirrors today's fixture) |
| Tenant 3 | Beneficiário 1 - Tenant 3 (Pessoa 5), Beneficiário 2 - Tenant 3 (Pessoa 6) |
| Tenant 4 | Beneficiário 1 - Tenant 4 (Pessoa 7) |

Pessoa 4 and Pessoa 8 remain unlinked to any Beneficiário — Pessoa 4 is deliberately kept that way
(the fixture ~7 existing tests build new Beneficiário rows against); Pessoa 8 is a spare for
manual testing.

## Test file edits (source of truth for `/speckit-tasks`)

See research.md §5 for the full list and rationale. Summary: 3 files need a literal display-value
edit, 6 more need a doc-comment-only edit, everything else needs no change because it only
references seed data via `AbstractIntegrationTest`'s constants or unchanged ids.
