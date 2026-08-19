# Research: Clearer, Expanded Demo Seed Data

## §1. Making demo-data insertion optional

**Decision**: Tag every demo-data changeset (`002-seed-demo-data.sql`, the new
`004-role-system-seed-data.sql`, the new `005-seed-data-relabel-and-expand.sql`) with Liquibase's
`context:demo` changeset attribute. Set `spring.liquibase.contexts: demo` only when a Spring
`demo` profile is active, via a second YAML document appended to the existing `application.yml`
(`spring.config.activate.on-profile: demo`), and set `spring.profiles.active: demo` as the
**default** in that same file's base document. Structural changesets (`001-schema.sql`, the
schema-only remainder of `003-role-system.sql`) carry no context and always run.

**Rationale**: Because `spring.profiles.active: demo` is set as a *default* inside
`application.yml` itself, it is automatically overridden by an external `SPRING_PROFILES_ACTIVE`
environment variable (Spring Boot's property-source precedence puts OS env vars above the
classpath `application.yml`) — so the default (no override) keeps demo data on, satisfying spec
FR-015, while anyone who wants a clean database sets `SPRING_PROFILES_ACTIVE=` (empty) or any
other profile name and gets none, satisfying FR-014. No `docker-compose.yml` change is required
for the default path. Liquibase's own context-matching rule is what makes "on by default"
possible in the first place: a changeset **without** a context always runs regardless of the
active context set, while a changeset **with** a context only runs if that context is in the
active set — so `spring.liquibase.contexts` must default to `demo` (not default to empty), or the
default startup path would stop seeding data, which would violate FR-015.

**Test-suite impact**: `AbstractIntegrationTest` (and everything extending it) never sets
`@ActiveProfiles`, so it inherits the same "demo profile active by default" behavior as
`docker-compose` — the existing integration suite keeps seeing demo data exactly as before,
with no test-configuration change needed for this mechanism itself.

**Alternatives considered**:
- *A raw environment variable checked directly (e.g. `APP_SEED_DEMO_DATA`), independent of Spring
  profiles.* Rejected — this is what Clarifications Q2 explicitly asked about and decided against
  in favor of "a dedicated startup profile," and profiles are the idiomatic Spring Boot mechanism
  for "a named bundle of environment-specific configuration" (of which "should demo data seed" is
  one example), rather than inventing a bespoke property.
- *A separate `application-demo.yml` file.* Rejected in favor of a second YAML document inside the
  existing `application.yml` (`---` separator + `spring.config.activate.on-profile`) — same
  effect, one fewer file, keeping all configuration in one place (Constitution Principle V).

## §2. Splitting the already-applied `003-role-system.sql`

**Decision**: Per Clarifications (Option A), split `003-role-system.sql` in place: keep only its
two `ALTER TABLE` statements (no context — always runs), and move its `INSERT`
(the seeded System Admin) and `UPDATE` (bruno's `is_tenant_admin` flag) into a new
`004-role-system-seed-data.sql`, tagged `context:demo`.

**Rationale**: This is the one existing changeset that mixes a structural change with demo-data
seeding — every other existing changeset (`001` schema-only, `002` data-only) already follows the
separation spec FR-013 requires. Editing an already-applied changeset's content changes its
Liquibase checksum, which the user's accepted answer to Clarifications Q1 explicitly costs:
anyone with an existing local database must recreate it (`docker compose down -v`) to pick this
change up. Since that cost is already accepted for this one file, the plan also folds
`002-seed-demo-data.sql`'s required `context:demo` tag (§1) into the *same* accepted recreate —
tagging `002` also changes its checksum, but it is the same one-time event, not an additional one.

**Test-suite impact**: none beyond what §1 already covers — `AbstractIntegrationTest`'s
Testcontainers Postgres is always a fresh container per JVM run (never a pre-existing volume), so
changeset checksums are computed fresh every time; the split is invisible to tests either way.

**Alternatives considered**: See spec.md Clarifications — Option B (leave `003` as immutable
history, apply separation only going forward) was the initial recommendation but was explicitly
overridden by the user in favor of Option A.

## §3. Renaming existing rows without changing ids

**Decision**: `005-seed-data-relabel-and-expand.sql` renames existing rows via `UPDATE` (matched
by their existing, unchanged `id`), then adds new rows via `INSERT`. Every id present today stays
present and unchanged.

| id | old value | new value |
|---|---|---|
| tenant `1111...1111` | "Tenant Alfa" | **"Tenant 1"** |
| tenant `2222...2222` | "Tenant Beta" | **"Tenant 2"** |
| app_user `3333...3333` (ana) | "ana" | **"User 1 - NORMAL"** (stays: member of Tenant 1 + Tenant 2, no admin) |
| app_user `4444...4444` (bruno) | "bruno" | **"User 2 - TENANT ADMIN"** (stays: Tenant Admin of Tenant 1 only) |
| app_user `7777...7777` (admin) | "admin" | **"User 3 - ADMIN"** (stays: System Admin, zero memberships) |
| pessoa `5555...551` (Maria Silva) | — | **"Pessoa 1"** (stays: referenced by beneficiarios in both Tenant 1 and 2) |
| pessoa `5555...552` (João Souza) | — | **"Pessoa 2"** |
| pessoa `5555...553` (Carla Pereira) | — | **"Pessoa 3"** (stays: the Tenant-2-only beneficiario's pessoa) |
| pessoa `5555...554` (Pedro Santos) | — | **"Pessoa 4"** (stays: the "no beneficiario yet" fixture ~7 tests build on) |
| beneficiario `6666...661` | "MAT-A-001" | **"Beneficiário 1 - Tenant 1"** |
| beneficiario `6666...662` | "MAT-A-002" | **"Beneficiário 2 - Tenant 1"** |
| beneficiario `6666...663` | "MAT-B-001" | **"Beneficiário 1 - Tenant 2"** |
| beneficiario `6666...664` | "MAT-B-002" | **"Beneficiário 2 - Tenant 2"** |

**Rationale**: Reviewers only ever read display fields, never internal ids (spec Assumptions).
Keeping ids stable means the 25+ id/CPF literals already hardcoded across the integration test
suite keep working unmodified — only 3 files hardcode a *display value* (not an id), so only
those need an edit (see data-model.md).

**Alternatives considered**: Renumbering ids too (e.g., clean sequential UUIDs matching the new
display numbers). Rejected as unnecessary risk — ids are never read by a human, so renumbering
them serves no part of spec FR-001–004, while touching every test file that hardcodes one of the
25+ existing id/CPF literals for no behavioral benefit.

## §4. New rows ("add more data")

**Decision**: Add, via `INSERT` in `005-seed-data-relabel-and-expand.sql`:
- **Tenant 3**, **Tenant 4**
- **User 4 - TENANT ADMIN** — Tenant Admin of Tenant 3 only (independent of User 2's Tenant 1
  standing, satisfying spec FR-008)
- **User 5 - NORMAL** — member of Tenant 3 + Tenant 4
- **User 6 - NORMAL** — member of Tenant 2 only (satisfying spec FR-009: a Normal member of
  exactly one tenant)
- **Pessoa 5, 6, 7, 8** — freshly computed, check-digit-valid CPFs (verified against
  `CpfValidator`'s exact algorithm; see below), none colliding with existing seeded or
  test-hardcoded CPFs
- **Beneficiário 1 - Tenant 3** (Pessoa 5), **Beneficiário 2 - Tenant 3** (Pessoa 6),
  **Beneficiário 1 - Tenant 4** (Pessoa 7) — gives every tenant ≥1 beneficiário (spec FR-010);
  Pessoa 8 stays unlinked, free for manual testing

**New ids**: Distinguishable-at-a-glance from the existing numeric-block ids, using hex-letter
blocks (still valid UUID text — `0-9a-f` per group): tenants `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa{1,2}`,
users `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb{1,2,3}`, pessoas
`cccccccc-cccc-cccc-cccc-ccccccccccc{1,2,3,4}`, beneficiarios
`dddddddd-dddd-dddd-dddd-ddddddddddd{1,2,3}`.

**New CPFs** (computed with the same weighted check-digit algorithm as
`backend/src/main/java/com/tbm/common/validation/CpfValidator.java`, verified not to collide with
any CPF already seeded or hardcoded in the test suite): `12345678909`, `23456789173`,
`98765432100`, `11223344517`.

All new users share the same bcrypt hash already used for every other seeded user
(`$2b$10$K1faUDGXmVIgKrNryKGFaOEK4hECPRiNkz6qokAmAPbfklbmaTDo6` = `demo123`), satisfying spec
FR-006.

## §5. Test file edits

Confirmed by repo-wide grep: only 3 files hardcode a seed *display value* rather than an id or a
centralized constant from `AbstractIntegrationTest`:

- `TenantUpdateAuthorizationTest.java` — 2 occurrences of literal `"Tenant Alfa"` (a `finally`
  cleanup that renames the tenant back after a test) → `"Tenant 1"`
- `PessoaDeletionRestrictionTest.java` — an anti-leak assertion checking the conflict message
  doesn't contain `"tenant alfa"` / `"tenant beta"` → `"tenant 1"` / `"tenant 2"`
- `TenantIsolationTest.java` — literal matricula `"MAT-B-001"` in a PUT body (not load-bearing for
  the assertion itself, updated for consistency) → `"Beneficiário 1 - Tenant 2"`

6 more files get a doc-comment-only accuracy edit (zero behavioral risk):
`AbstractIntegrationTest.java`, `TenantCrudTest.java`, `TenantAdminGrantRevokeTest.java`,
`MembershipManagementTest.java`, `BeneficiarioCreationTest.java`,
`TenantMembershipEnforcementTest.java`.

Every other integration test file references seed data only via `AbstractIntegrationTest`'s
constants or raw ids, neither of which change — no edit needed.

Two frontend Vitest files (`TenantSwitcher.spec.js`, `TenantFormView.spec.js`) use similarly-named
mock data but with entirely their own fake ids — confirmed self-contained, not coupled to the real
seed data. Left untouched.
