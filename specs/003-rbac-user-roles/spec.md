# Feature Specification: Role-Based Access for Users

**Feature Branch**: `003-rbac-user-roles`

**Created**: 2026-08-17

**Status**: Draft

**Input**: User description: "Add a role system to the users entity, with the following roles: 1 - System Admin for users that can manage the tenants (create, read, update and delete them). 2 - Tenant Admin for users that can manage the of the tenant they have access as Tenant Admin. 3 - Normal user, that can just manage pessoas and beneficiários of the tenants they belong. The System Admin have permission to all the features that Tenant Admin and a normal user have, and the Tenant Admin have the permission to all the features that a normal user has"

## Clarifications

### Session 2026-08-17

- Q: Should granting or revoking a user's System Admin standing be an in-app capability of this feature, or is System Admin assignment out of scope for now (set only via seed data / direct database access)? → A: In-scope — a System Admin can promote/demote other users' System Admin standing in-app.
- Q: Should granting or revoking a user's Tenant Admin standing for a specific tenant be restricted to System Admins only, or should an existing Tenant Admin of that tenant also be able to promote another member to Tenant Admin there? → A: Tenant Admins can also promote (and revoke) a member of their own tenant to/from Tenant Admin standing, not just System Admin.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - System Admin manages the platform's tenants and admins (Priority: P1)

A System Admin creates, reviews, updates, and removes Tenant records for the
platform, and grants or revokes other users' System Admin standing — neither
capability exists for any user today, at any tier.

**Why this priority**: This is the entirely new capability the feature
introduces (today tenants only exist as fixed seed data with no management
surface, and there is no way to change who has elevated standing at all); it
is also the top of the permission hierarchy every other tier inherits from.

**Independent Test**: Signed in as a System Admin, create a new Tenant, see it
appear wherever tenants are listed, edit its name, delete a Tenant that has no
Beneficiário records or user memberships, and grant System Admin standing to
another user — all without needing any other role.

**Acceptance Scenarios**:

1. **Given** a signed-in System Admin, **When** they submit a new Tenant with a
   valid name, **Then** the Tenant is created and appears in the platform's
   tenant listing.
2. **Given** a signed-in System Admin, **When** they edit an existing Tenant's
   details, **Then** the change is saved and reflected everywhere the Tenant is
   shown.
3. **Given** a Tenant with no Beneficiário records and no user memberships,
   **When** a System Admin deletes it, **Then** the Tenant is removed.
4. **Given** a Tenant that still has at least one Beneficiário record or user
   membership, **When** a System Admin attempts to delete it, **Then** the
   deletion is blocked with a clear explanation of why.
5. **Given** a signed-in user who is not a System Admin and does not hold
   Tenant Admin standing for a given Tenant, **When** they attempt to create,
   update, or delete that Tenant (through any available means), **Then** the
   action is denied with a clear error.
6. **Given** a signed-in user who is not a System Admin, **When** they attempt
   to create or delete any Tenant — including one where they hold Tenant Admin
   standing — **Then** the action is denied with a clear error, since create
   and delete remain exclusive to System Admin.
7. **Given** a signed-in System Admin, **When** they grant System Admin
   standing to another user, **Then** that user immediately gains System Admin
   standing platform-wide.
8. **Given** more than one user currently holds System Admin standing, **When**
   a System Admin revokes another System Admin's standing, **Then** the
   revoked user immediately loses System Admin standing.
9. **Given** exactly one user currently holds System Admin standing, **When**
   any attempt is made to revoke that last remaining System Admin's standing
   (including by that user themselves), **Then** the action is denied with a
   clear error explaining that the platform must always retain at least one
   System Admin.
10. **Given** a signed-in user who is not a System Admin, **When** they attempt
    to grant or revoke another user's System Admin standing, **Then** the
    action is denied with a clear error.

---

### User Story 2 - Tenant Admin manages their own tenant's membership and details (Priority: P2)

A Tenant Admin — a user granted elevated standing within one specific tenant —
adds and removes other users' membership in that tenant, edits that tenant's
own details (e.g., its name), and grants or revokes Tenant Admin standing for
other members of that same tenant — all without needing System Admin standing
and without being able to touch any tenant where they do not hold that
elevated standing.

**Why this priority**: This is the feature's second new capability (today no
one can manage tenant membership or edit a tenant's details at all — both are
fixed seed data) and it is what actually distinguishes "Tenant Admin" from
"Normal user" as a meaningfully different tier, rather than a tier with no
capability of its own.

**Independent Test**: Signed in as a Tenant Admin of Tenant A (and nothing more
than a Normal user of Tenant B), add an existing user's membership to Tenant A,
remove another user's membership from Tenant A, edit Tenant A's name, and
confirm any attempt to do the same in Tenant B is denied.

**Acceptance Scenarios**:

1. **Given** a Tenant Admin of Tenant A, **When** they add an existing user to
   Tenant A's membership, **Then** that user gains access to Tenant A (at the
   Normal user tier, unless separately granted Tenant Admin standing).
2. **Given** a Tenant Admin of Tenant A, **When** they remove a user's
   membership from Tenant A, **Then** that user immediately loses access to
   Tenant A's Beneficiário data.
3. **Given** a Tenant Admin of Tenant A, **When** they edit Tenant A's name,
   **Then** the change is saved and reflected everywhere Tenant A is shown.
4. **Given** a user who is a Tenant Admin of Tenant A only, **When** they
   attempt to add/remove a membership in Tenant B or edit Tenant B's details,
   **Then** the action is denied with a clear error, identically to how a
   Normal user would be denied.
5. **Given** a Tenant Admin of Tenant A, **When** they view Tenant A's member
   list, **Then** they see every user currently associated with Tenant A and
   each member's tier within that tenant.
6. **Given** a Tenant Admin of Tenant A, **When** they attempt to create a new
   Tenant or delete Tenant A, **Then** the action is denied with a clear error,
   since create and delete remain exclusive to System Admin.
7. **Given** a Tenant Admin of Tenant A, **When** they grant Tenant Admin
   standing to another member of Tenant A, **Then** that member immediately
   gains Tenant Admin standing for Tenant A only.
8. **Given** a Tenant Admin of Tenant A, **When** they revoke another member's
   Tenant Admin standing for Tenant A, **Then** that member immediately loses
   Tenant Admin standing for Tenant A (remaining a Normal-tier member unless
   they hold no other standing there).
9. **Given** a Tenant Admin of Tenant A only, **When** they attempt to grant or
   revoke Tenant Admin standing for Tenant B, **Then** the action is denied
   with a clear error.
10. **Given** a Tenant Admin of Tenant A, **When** they attempt to grant Tenant
    Admin standing to a user who is not a member of Tenant A, **Then** the
    action is rejected with a clear error, and no membership is created as a
    side effect.
11. **Given** a Tenant Admin of Tenant A grants Tenant Admin standing to
    another member of Tenant A, **When** that newly-promoted member makes
    their very next request, **Then** it already reflects their new standing
    (FR-015) — no re-login required.
12. **Given** a Tenant Admin of Tenant A, **When** they revoke their own
    Tenant Admin standing for Tenant A, **Then** they immediately become a
    Normal-tier member of Tenant A (no last-Tenant-Admin protection applies,
    since System Admin standing always remains available platform-wide).

---

### User Story 3 - Normal user keeps working exactly as today (Priority: P3)

A user with no elevated role — the default tier for any tenant membership —
continues to create, view, edit, and delete Pessoa records globally and
Beneficiário records within the tenants they belong to, exactly as the
platform already behaves today. This story exists to make explicit that the
feature adds new capabilities on top of the existing baseline rather than
narrowing what already works.

**Why this priority**: Lowest priority because it is a non-regression
guarantee, not new functionality — but it must hold, since every System Admin
and Tenant Admin action layers on top of this baseline rather than replacing
it.

**Independent Test**: Signed in as a user with no elevated role, perform the
same Pessoa and Beneficiário operations available before this feature existed,
and confirm nothing about that experience has changed.

**Acceptance Scenarios**:

1. **Given** a user with no elevated role, **When** they create, view, edit, or
   delete a Pessoa record, **Then** the action succeeds exactly as it does
   today, regardless of tenant membership.
2. **Given** a user with no elevated role who belongs to a tenant, **When**
   they create, view, edit, or delete a Beneficiário record within that tenant,
   **Then** the action succeeds exactly as it does today.
3. **Given** a user with no elevated role, **When** they attempt any Tenant
   management action or any tenant-membership management action, **Then** the
   action is denied with a clear error.

---

### Edge Cases

- What happens when the platform's last remaining System Admin is about to be
  demoted (via FR-014's revoke capability, including by themselves) or have
  their account removed? (Expected: blocked, to prevent a state where no one
  can manage tenants or grant System Admin standing to anyone else.)
- What happens when a Tenant Admin of Tenant A, who is also merely a Normal
  user (or not a member at all) of Tenant B, attempts a Tenant-Admin-tier
  action against Tenant B? (Expected: denied identically to a Normal user
  attempting the same thing — no indication of Tenant B's data is revealed,
  consistent with the platform's existing cross-tenant isolation guarantee.)
- What happens when a System Admin who does not hold membership in a
  particular tenant performs a Beneficiário or membership action in that
  tenant? (Expected: allowed — System Admin standing is platform-wide and does
  not depend on holding a membership row in every tenant; see Assumptions.)
- What happens to a user who has a tenant membership but no elevated role at
  all in it? (Expected: treated as Normal user for that tenant — this is the
  default, not an error state.)
- What happens when a System Admin attempts to remove their own last
  membership or demote themselves in a way that would leave the platform with
  zero System Admins? (Expected: blocked, same as the last-System-Admin edge
  case above.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support three permission tiers for a user's standing
  on the platform: System Admin (platform-wide), Tenant Admin (granted per
  individual tenant membership — a user may hold it for one tenant and not
  another), and Normal user (the default tier for any tenant membership that
  has no elevated role).
- **FR-002**: System MUST allow any user holding System Admin standing to
  create, view, update, and delete Tenant records.
- **FR-003**: System MUST block deletion of a Tenant that still has at least
  one Beneficiário record or at least one user membership referencing it, and
  MUST explain why the deletion was blocked.
- **FR-004**: System MUST deny Tenant create and delete actions to any user who
  does not hold System Admin standing, with a clear error. Updating a Tenant's
  own attributes (e.g., its name) MUST additionally be allowed for a user
  holding Tenant Admin standing for that specific tenant (see FR-005).
- **FR-005**: System MUST allow a user holding Tenant Admin standing in a given
  tenant to (a) add and remove other users' membership in that same tenant,
  (b) update that Tenant record's own attributes (e.g., its name), and (c)
  grant or revoke Tenant Admin standing — including their own — for any user
  who already holds membership in that same tenant (granting Tenant Admin
  standing to a non-member is rejected; it does not itself create membership,
  which remains a separate action per (a)) — but not create or delete the
  Tenant itself, which remains exclusive to System Admin standing, and never
  grants any System Admin capability (see FR-002/FR-014). Granting standing
  already held, or revoking standing not held, is a no-op that succeeds
  without error.
- **FR-006**: System MUST deny a user holding Tenant Admin standing in one
  tenant from exercising that standing in any other tenant where they do not
  hold it, identically to how a Normal user would be denied.
- **FR-007**: System MUST continue to allow any authorized user — regardless of
  role tier — to create, view, update, and delete Pessoa records globally, and
  to create, view, update, and delete Beneficiário records within tenants they
  belong to, unchanged from the platform's existing behavior.
- **FR-008**: System MUST grant a user holding System Admin standing every
  action available to Tenant Admin standing and every action available to
  Normal user standing, across every tenant platform-wide, regardless of
  whether that System Admin also holds a membership row in a given tenant.
  FR-006's per-tenant restriction applies only to Tenant Admin standing; it
  never limits System Admin standing, which has no per-tenant boundary to
  cross.
- **FR-009**: System MUST grant a user holding Tenant Admin standing in a given
  tenant every action available to Normal user standing within that same
  tenant.
- **FR-010**: System MUST deny a user with no elevated standing (Normal user
  tier) any Tenant-management action and any tenant-membership-management
  action, with a clear error.
- **FR-011**: System MUST prevent an action that would leave the platform with
  zero users holding System Admin standing (whether through FR-014's grant/
  revoke capability or by deleting the last System Admin's account). This
  count check and the action it guards MUST be enforced atomically, so that
  two concurrent revoke/delete requests against the last two remaining System
  Admins cannot both succeed and jointly leave zero.
- **FR-012**: System MUST ship with at least one pre-configured System Admin
  user, at least one pre-configured Tenant Admin (holding that standing in at
  least one seeded tenant), and continue to include users with no elevated
  standing — all available immediately with no manual setup step, so every
  tier is demonstrable from seed data alone.
- **FR-013**: System MUST return a clear, specific error — consistent with the
  platform's existing error format — whenever a user attempts an action their
  role tier does not permit.
- **FR-014**: System MUST allow a user holding System Admin standing to grant
  System Admin standing to any user — including themselves, though a user
  already seeded or promoted as System Admin gains nothing by re-granting it
  to themselves — and to revoke any user's System Admin standing, including
  their own, subject to FR-011's protection against removing the last
  remaining System Admin. Granting standing already held, or revoking
  standing not held, is a no-op that succeeds without error. Granting or
  revoking against a `userId` that does not exist MUST return a not-found
  error.
- **FR-015**: A change to a user's System Admin standing or Tenant Admin
  standing MUST take effect for that user's very next request, regardless of
  how much longer their existing signed-in session's credential would
  otherwise remain valid — no re-login or manual refresh required.
- **FR-016**: System MUST provide a way for a Tenant Admin or System Admin to
  look up an existing user (e.g., by exact username) so they can be
  referenced when adding a tenant membership (FR-005(a)).

### Key Entities

- **User**: Unchanged core identity (unique username, credential). Gains a
  platform-wide flag indicating System Admin standing, independent of any
  tenant membership.
- **User-Tenant Membership**: The existing association between a User and a
  Tenant. Gains a per-membership indicator of whether that specific membership
  carries Tenant Admin standing for that tenant, in addition to the tenant
  access it already grants (unchanged).
- **Tenant**: Unchanged in shape (a client of the platform defining a
  visibility boundary for Beneficiário records), but gains, for the first
  time, a management surface: it can now be created and deleted by a System
  Admin, and its own attributes (e.g., name) updated by a System Admin or by a
  Tenant Admin of that specific tenant — not only pre-seeded and immutable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A System Admin can create a new Tenant and have it fully usable
  (assignable to memberships, ready to hold Beneficiário records) in a single
  action.
- **SC-002**: 100% of Tenant create/delete attempts by a user without System
  Admin standing are denied, observed during testing — including a user
  holding Tenant Admin standing for that tenant.
- **SC-003**: 100% of tenant-membership add/remove attempts, tenant-detail
  edit attempts, and Tenant-Admin-standing grant/revoke attempts by a user
  without the appropriate Tenant Admin (or System Admin) standing for that
  specific tenant are denied, observed during testing.
- **SC-004**: A Tenant Admin can add or remove a tenant membership, edit their
  tenant's details, or grant/revoke another member's Tenant Admin standing,
  and see the effect reflected within a single action, with no manual refresh
  step.
- **SC-005**: 100% of existing Pessoa and Beneficiário operations available to
  a Normal user before this feature continue to succeed exactly as before,
  observed during testing (zero regressions).
- **SC-006**: A reviewer can open the running system with no manual setup and
  observe all three role tiers in action (System Admin managing a tenant,
  Tenant Admin managing membership, Normal user managing Beneficiários) using
  only pre-configured accounts.
- **SC-007**: Any attempt to remove the platform's last System Admin — whether
  by revoking their standing or deleting their account — is blocked 100% of
  the time, observed during testing, including under concurrent attempts.
- **SC-008**: A System Admin can grant another user System Admin standing, or
  revoke it, in a single action, with the effect immediately observable both
  to the granter (in their own view of that user's standing) and to the
  affected user (on their own very next request, per FR-015).
- **SC-009**: 100% of attempts by a user without System Admin standing to
  grant or revoke another user's System Admin standing are denied, observed
  during testing.
- **SC-010**: A user whose Tenant Admin or System Admin standing is revoked
  loses the corresponding elevated access on their very next request, 100% of
  the time, observed during testing.

## Assumptions

- Pessoa records remain a single global registry, unaffected by this feature
  and by role tier: the platform's existing, non-negotiable rule that Pessoa is
  never filtered or restricted by tenant is unchanged. The feature description's
  mention of "pessoas... of the tenants they belong" is treated as informal
  phrasing for "Pessoa records, plus Beneficiário records scoped to tenants
  they belong to" — not a new tenant-scoping rule for Pessoa.
- A System Admin or a Tenant Admin of that same tenant can grant or revoke
  another user's Tenant Admin standing for that tenant (per FR-005/FR-006);
  this is a bounded, same-tier delegation — a Tenant Admin can only ever
  create more Tenant Admins within their own tenant, never grant System Admin
  standing or act on any other tenant.
- Existing pre-configured users retain their current (Normal user tier)
  standing unless explicitly assigned an elevated tier by the seed data this
  feature adds; no existing user's access is reduced by this feature.
- Deleting a Tenant follows the same referential-safety-over-cascading-delete
  posture the platform already applies to Pessoa deletion: blocked while
  dependent records exist, rather than cascading.
- Unlike System Admin standing (FR-011), Tenant Admin standing has no
  minimum-count protection: a tenant may legitimately have zero Tenant
  Admins at any time, because System Admin standing (FR-008) already carries
  every Tenant Admin capability platform-wide and can always step in — there
  is no scenario where a tenant is left unmanageable.
- A user who holds both System Admin standing and Tenant Admin standing for a
  specific tenant experiences no special or conflicting behavior: System
  Admin standing already grants every Tenant Admin capability everywhere
  (FR-008), so the Tenant Admin flag on that membership is redundant but
  harmless, and revoking it (or not) has no effect on that user's actual
  access.
- Removing a user's membership in a tenant and revoking that same membership's
  Tenant Admin standing are not required to be atomic with each other:
  removing the membership already implies losing any standing attached to it
  (FR-005(a)), so either action arriving first (or concurrently) converges on
  the same end state — no membership, no standing.
- No upper bound is placed on how many users may simultaneously hold System
  Admin standing, or Tenant Admin standing within a single tenant.
- Rate-limiting or throttling of grant/revoke attempts is out of scope for
  this feature, consistent with the platform's existing stance of not
  rate-limiting any other endpoint.
- Audit logging of who granted or revoked which standing, and when, is out of
  scope for this feature; the platform does not currently log any other
  business action (e.g., Pessoa/Beneficiário edits) either, and this feature
  does not change that baseline.
