# Feature Specification: Clearer, Expanded Demo Seed Data

**Feature Branch**: `005-seed-data-relabel`

**Created**: 2026-08-18

**Status**: Draft

**Input**: User description: "Modify the seed data, making the names of the tenants, pessoas, users and beneficiarios more straightforward, like \"Pessoa 1\", \"Tenant 1\", \"Beneficiário 1 - Tenant 1\" and \"User 1 - ADMIN\". Making it clearer to see if it should be listed or not. And add more data. Also, refactor the db files, to make the inserts of demo data in a separate migration. And make the demo data optional when starting the application."

## Clarifications

### Session 2026-08-18

- Q: Should the already-applied changeset that mixes a schema change with demo-data seeding be retroactively split into separate schema and data migrations too, or should the schema/data separation only apply going forward, to new migrations? → A: Retroactively split the already-applied changeset in place into a schema-only and a data-only migration, replacing it — accepting that anyone with an existing local database must recreate it (e.g., `docker compose down -v`) to pick up the change.
- Q: How should demo-data seeding become optional at startup, and should it be on or off by default? → A: A dedicated startup profile gates whether the demo-data migration(s) run; the default local/Docker startup keeps demo data on, so today's zero-touch experience is unchanged, while starting the application without that profile yields a database with no demo data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Self-describing demo data (Priority: P1)

As someone manually testing, reviewing, or demoing the application, I want every demo Tenant, Pessoa, User, and Beneficiário to have a plain, numbered, self-describing name, so I can tell what a record is and which tenant it belongs to just by reading it — without cross-referencing an ID or asking someone else.

**Why this priority**: This is the entire point of the change. Today's demo names ("Tenant Alfa", "ana", "Maria Silva") read naturally but give no clue about scope or standing, making it easy to misjudge whether a given screen or record is showing the right thing during a manual check.

**Independent Test**: Can be fully tested by listing every seeded Tenant, User, Pessoa, and Beneficiário and confirming each name alone (no lookup required) reveals what kind of record it is, and — for Users and Beneficiários — which tenant and/or role it's tied to.

**Acceptance Scenarios**:

1. **Given** the list of seeded Tenants, **When** a reviewer reads the names, **Then** each one reads as "Tenant `<N>`" for a distinct number `N`.
2. **Given** the list of seeded Users, **When** a reviewer reads the usernames, **Then** each one reads as "User `<N>` - `<ROLE>`", where `<ROLE>` is ADMIN, TENANT ADMIN, or NORMAL, immediately revealing that user's standing without logging in or checking a database.
3. **Given** the list of seeded Beneficiários for a given tenant, **When** a reviewer reads their labels, **Then** each one reads as "Beneficiário `<N>` - Tenant `<M>`", identifying both the record and its owning tenant.
4. **Given** the list of seeded Pessoas, **When** a reviewer reads the names, **Then** each one reads as "Pessoa `<N>`".

---

### User Story 2 - Broader demo coverage (Priority: P2)

As someone testing tenant- and role-scoped behavior, I want more demo Tenants, Users, Pessoas, and Beneficiários than exist today — including more than one independent Tenant Admin and a user who belongs to only a single tenant — so I can exercise a wider range of realistic scenarios without first creating data by hand.

**Why this priority**: Valuable on its own once User Story 1's naming convention exists — more data has limited benefit if it's still hard to tell what's what, so the naming clarity comes first, but the expanded roster is a distinct, separately deliverable improvement.

**Independent Test**: Can be fully tested by counting the seeded Tenants/Users/Pessoas/Beneficiários before and after this change and confirming a meaningful increase, plus confirming at least one newly added Tenant Admin is independent of (has no standing in) any tenant another Tenant Admin administers, and at least one newly added Normal user belongs to exactly one tenant.

**Acceptance Scenarios**:

1. **Given** the expanded seed data, **When** counted, **Then** the number of Tenants, Users, Pessoas, and Beneficiários available for testing is measurably greater than before this change.
2. **Given** the expanded seed data, **When** reviewed, **Then** at least two Users independently hold Tenant Admin standing for two different tenants (neither administers the other's tenant).
3. **Given** the expanded seed data, **When** reviewed, **Then** at least one User is a Normal member of exactly one tenant (not multiple).

---

### Edge Cases

- What happens to the existing relationships (which tenants a user belongs to, who holds System Admin or Tenant Admin standing and for which tenant) that today's demo data already establishes? They MUST be preserved exactly — this change relabels records, it does not change who can do what.
- What happens to the shared demo password used to log in as any seeded user? It MUST remain the same for every seeded user, existing and newly added, so today's documented login instructions keep working with only the usernames updated.
- What happens if a newly added Tenant has no associated records at all? Every seeded Tenant MUST have at least one Beneficiário, so every tenant behaves consistently for anyone testing tenant-level actions (e.g., deletion) against demo data.
- What happens to anything that already depends on today's exact demo names (automated verification, written instructions)? It MUST be updated as part of this change so nothing is left referencing stale names; nothing about the underlying system's behavior may change as a result.
- What happens when the application starts with demo-data insertion skipped? It MUST still start successfully with a fully set-up, empty-of-demo-data structure. As of this feature, the system has no account-creation path other than demo-data seeding, so this MUST be understood as a schema-verification/clean-slate mode, not a usable deployment mode — no one can log in afterward, and adding an account-creation path is explicitly out of scope for this feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every seeded Tenant's name MUST follow the pattern "Tenant `<N>`" for a sequential number `<N>`.
- **FR-002**: Every seeded Pessoa's name MUST follow the pattern "Pessoa `<N>`" for a sequential number `<N>`.
- **FR-003**: Every seeded User's username MUST follow the pattern "User `<N>` - `<ROLE>`", where `<ROLE>` is ADMIN, TENANT ADMIN, or NORMAL, matching that user's actual standing.
- **FR-004**: Every seeded Beneficiário's identifying label MUST follow the pattern "Beneficiário `<N>` - Tenant `<M>`", identifying both the beneficiário and the tenant it belongs to.
- **FR-005**: Renaming existing seed records MUST NOT change any existing user's tenant memberships, System Admin standing, or Tenant Admin standing — only display names change, not who can do what.
- **FR-006**: The demo password for every seeded user, existing and newly added, MUST remain the single shared password already used today.
- **FR-007**: The seed data MUST include more Tenants, Users, Pessoas, and Beneficiários than exist today.
- **FR-008**: The expanded seed data MUST include at least one additional Tenant Admin whose standing is independent of (does not overlap with) any tenant another seeded Tenant Admin administers.
- **FR-009**: The expanded seed data MUST include at least one User who is a Normal member of exactly one tenant.
- **FR-010**: Every seeded Tenant, existing and newly added, MUST have at least one associated Beneficiário.
- **FR-011**: This change MUST NOT alter the behavior of any already-established automated verification for the system — such verification MUST continue to pass unmodified in its assertions after the seed data changes, updated only where it directly references an old name/label that no longer exists.
- **FR-012**: Any existing written reference to today's demo names (e.g., documented login credentials) MUST be updated to match the new names as part of this change.
- **FR-013**: Demo-data insertion MUST be delivered as a migration separate from any migration that defines or alters the underlying data structure. This applies both going forward and retroactively: the one existing migration that currently mixes a structural change with demo-data seeding MUST be split in place into a structure-only migration and a data-only migration, even though this requires anyone with an existing local database to recreate it to pick up the change.
- **FR-014**: Starting the application MUST support skipping demo-data insertion entirely via a startup-time toggle, leaving the underlying data structure fully set up but with no demo Tenants, Users, Pessoas, Beneficiários, or memberships present.
- **FR-015**: The default local/Docker startup path documented for this project MUST continue to insert demo data automatically with no extra step, exactly as it does today; skipping demo data MUST be something a starter has to actively choose, not the default outcome of following the documented quick-start instructions.

### Key Entities

- **Tenant**: A demo organization/workspace in the system; this feature only changes its display name and adds more of them.
- **Pessoa**: A demo person record, shared across tenants; this feature only changes its display name and adds more of them.
- **User**: A demo login account; this feature changes its username to also state its role tier, preserves its existing tenant memberships/standing (if any), and adds more accounts covering additional role combinations.
- **Beneficiário**: A demo enrollment record linking a Pessoa to a Tenant; this feature changes its identifying label to state which tenant it belongs to and adds more of them so every tenant has at least one.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of seeded Tenants, Users, Pessoas, and Beneficiários have a name/label that, read on its own, correctly identifies what the record is and (for Users and Beneficiários) which tenant and/or role it is tied to.
- **SC-002**: The number of seeded Tenants, Users, Pessoas, and Beneficiários each increase by at least 50% over today's baseline.
- **SC-003**: 100% of already-established automated verification continues to pass after the change.
- **SC-004**: A reviewer can correctly state a seeded user's role tier (System Admin / Tenant Admin / Normal) from the username alone, with no incorrect guesses across all seeded users.
- **SC-005**: Following the project's documented default startup path results in demo data present, exactly as before this change; starting the application with the demo-data toggle turned off results in zero demo Tenants, Users, Pessoas, Beneficiários, or memberships, with the application still starting successfully and serving requests (e.g., its health check) — understanding that with no demo data and no other account-creation path, no one can log in in that mode (see Edge Cases).

## Assumptions

- Only human-readable display fields (Tenant name, Pessoa name, User username, Beneficiário label) change; internal record identifiers are not required to change, since reviewers only ever read the display fields, not internal identifiers.
- The demo password stays shared across all seeded users, consistent with the existing convention — per-user distinct passwords are out of scope.
- New demo Pessoa records use values that satisfy the platform's existing validation rules for that field (e.g., a validly formed identifier), the same as any record entered through the application itself.
- Seed data continues to be delivered as a versioned, automatically-applied migration, consistent with how this project already manages schema and demo data. Splitting the one existing migration that mixes structure and demo data (per Clarifications) is a one-time exception to "no manual step": anyone with an existing local database must recreate it once to pick up the split; every migration after that point requires no manual step, same as today.
- Updating already-established automated verification and written documentation to match the new names is considered part of delivering this change, not separate follow-up work.
- The application currently has no account-creation path outside of demo-data seeding. Adding one is out of scope for this feature; starting with demo data off is therefore a schema-verification/clean-slate check, not a usable standalone deployment mode, until a future feature adds another way to create an account.
