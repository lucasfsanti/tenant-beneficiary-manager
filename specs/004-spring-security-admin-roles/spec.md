# Feature Specification: Migrate Admin Role Verification to Spring Security Authorization

**Feature Branch**: `004-spring-security-admin-roles`

**Created**: 2026-08-18

**Status**: Draft

**Input**: User description: "migrate the verification of whether the user is a system admin or tenant admin to the Roles and Privileges of Spring Security"

## Clarifications

### Session 2026-08-18

- Q: When a System Admin's or Tenant Admin's standing is revoked while they still hold a valid, unexpired login session, should that revocation block them on their very next request, or only once they log in again? → A: Immediate — revocation must block access on the very next request, even mid-session, matching current behavior.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Centralized enforcement of admin-only operations (Priority: P1)

As a platform maintainer, I want System Admin and Tenant Admin authorization decisions enforced through the platform's centralized, declarative access-control layer instead of hand-written checks scattered across service methods, so that every current and future admin-gated capability is protected consistently and can't be accidentally left unprotected.

**Why this priority**: This is the core problem being solved. Today, each protected operation re-implements its own admin check by querying the database and branching in code; a new operation can be added without anyone remembering to add that check. Centralizing enforcement removes that risk and is the primary value of this migration.

**Independent Test**: Can be fully tested by confirming that a user without the required standing is blocked from every currently admin-protected operation, and that the protection is driven by the central authorization declaration for that operation rather than a parallel manual check — i.e., removing or changing the central declaration for one operation is sufficient, by itself, to change whether that operation is protected.

**Acceptance Scenarios**:

1. **Given** a user without System Admin standing, **When** they attempt to create a new Tenant, list all Tenants, or delete a Tenant, **Then** the request is rejected before any business logic runs, based on the centrally declared authorization rule for that operation.
2. **Given** a user with Tenant Admin standing for Tenant A only, **When** they attempt to view or edit details, or manage membership, for Tenant B, **Then** the request is rejected, because their Tenant Admin standing is scoped to Tenant A and does not carry over to Tenant B — even though that same user could perform those actions on Tenant A.
3. **Given** a user with System Admin standing, **When** they perform any operation currently reserved for System Admins, **Then** the request succeeds exactly as it did before the migration.

---

### User Story 2 - Auditable, single-source access-control coverage (Priority: P2)

As a security reviewer, I want a single, inspectable definition of which standing (System Admin, Tenant Admin, or any authenticated user) each protected operation requires, so I can audit access-control coverage without reading through service implementation bodies.

**Why this priority**: Reduces the effort and risk of manual code review to confirm access-control coverage, but the system is still functionally protected without this (User Story 1 alone already enforces the rules); this story is about the review/audit experience of that enforcement.

**Independent Test**: Can be fully tested by taking the list of operations that currently require admin standing and confirming each one's required standing is discoverable in one central place, with no operation relying on an inline conditional buried inside a service method.

**Acceptance Scenarios**:

1. **Given** the list of currently admin-gated operations, **When** reviewed against the central authorization declarations, **Then** each operation's required standing (System Admin, Tenant Admin, or none) is stated explicitly and is discoverable without reading service method bodies.

---

### User Story 3 - Last-System-Admin safeguard keeps working (Priority: P3)

As an operator relying on the platform never being left without a System Admin, I want that safeguard to keep working after the authorization mechanism changes, so the last System Admin's standing still cannot be revoked.

**Why this priority**: This is an existing, already-working safeguard; it must not regress as a side effect of this migration, but it is not new behavior being introduced.

**Independent Test**: Can be fully tested by attempting to revoke System Admin standing from the sole remaining System Admin and confirming the action is still rejected.

**Acceptance Scenarios**:

1. **Given** exactly one System Admin exists on the platform, **When** an authorized System Admin attempts to revoke that last System Admin's standing, **Then** the action is rejected and at least one System Admin continues to exist.

---

### Edge Cases

- What happens when a user holds Tenant Admin standing for some tenants but not the specific tenant referenced in the current request? The request MUST be rejected — Tenant Admin standing is evaluated per tenant, not as a single global flag.
- What happens to an already-issued session/token when a user's admin standing is revoked mid-session? The very next request MUST be blocked — standing is evaluated fresh on every request, not fixed at login, matching current behavior.
- What happens for operations that only require the user to be authenticated (no admin standing at all)? These MUST remain unaffected — any authenticated user continues to be allowed.
- What happens when the sole remaining System Admin attempts to revoke their own standing? It MUST still be rejected, consistent with the last-System-Admin safeguard.
- What happens when a Tenant Admin action targets a tenant the user has no membership in at all (not just lacking admin standing there)? The request MUST be rejected with the identical response (same status and message) as when the user has membership but lacks Tenant Admin standing — the two cases are not distinguished today and MUST NOT become distinguished by this migration.
- What happens when a user holds no Tenant Admin standing in any tenant at all (e.g., never granted it, or removed from every tenant they administered)? Every tenant-scoped operation MUST be rejected for that user unless they also hold System Admin standing, identical to a user who holds Tenant Admin standing elsewhere but not for the tenant in question.
- What happens if a System Admin's standing is revoked by someone else while a request from that System Admin is already being processed? Only their *next* request is guaranteed to be blocked (per the freshness requirement above); this migration does not require interrupting a request already in flight.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST determine each request's System Admin standing (platform-wide) and Tenant Admin standing (scoped to a specific tenant) through a single, declarative authorization mechanism shared across every operation, replacing today's pattern where each service class repeats its own private, hand-written check. The required standing for an operation MUST be visible at the point the operation is defined, not only inside logic that runs when it executes — today, confirming an operation's protection requires reading into that class's check, even though the check itself is reusable within the class.
- **FR-002**: Every operation that requires System Admin standing today — whether System Admin is the only sufficient standing, or one of two sufficient standings alongside Tenant Admin — MUST continue to require System Admin standing, enforced through the centralized mechanism.
- **FR-003**: Every operation where Tenant Admin standing (scoped to the specific tenant being acted upon) is sufficient today — whether or not System Admin standing is *also* sufficient for that same operation — MUST continue to accept Tenant Admin standing for that tenant, enforced through the centralized mechanism. FR-002 and FR-003 are not disjoint: several operations satisfy both, requiring System Admin OR the relevant Tenant Admin.
- **FR-004**: Authorization decisions MUST be evaluated fresh on every request rather than fixed at login; revoking a user's System Admin or Tenant Admin standing MUST block them starting with their very next request, even within an already-active, unexpired session — matching current behavior.
- **FR-005**: A request denied for insufficient admin standing MUST return an HTTP 403 response with a structured, machine-readable error body — not a raw exception, stack trace, or generic error page — using the same body shape and title text the platform already returns for authorization failures today, so the change in enforcement mechanism produces no observable difference to callers.
- **FR-006**: The invariant that at least one System Admin must always exist on the platform MUST continue to be enforced, unaffected by the change in authorization mechanism.
- **FR-007**: The migration MUST NOT change what any user can or cannot currently do — for every operation gated today, the set of users authorized to perform it MUST be identical before and after the migration.
- **FR-008**: The required standing for any admin-protected operation MUST be discoverable from exactly one declaration attached to that operation itself — not a separate registry document, and not something requiring the reader to trace through that operation's implementation logic. ("Central" here means one declaration per operation, not one shared location for all operations.)
- **FR-009**: Operations that require no admin standing (any authenticated user) MUST remain unaffected by this migration.
- **FR-010**: This migration MUST NOT change the behavior of any operation in the affected service classes that does not currently perform an admin-standing check — converting the admin-gated operations in a class to the new mechanism MUST NOT alter, add a check to, or otherwise touch any sibling operation in that same class that today requires no admin standing.

### Key Entities

- **System Admin standing**: Platform-wide administrative status held by a user account; grants cross-tenant administrative capabilities (e.g., managing tenants, granting or revoking System Admin or Tenant Admin standing for others).
- **Tenant Admin standing**: Administrative status held by a user for one specific tenant they belong to; grants administrative capabilities scoped to that tenant only (e.g., managing that tenant's membership and details).
- **Protected Operation**: Any system capability that requires the acting user to hold a specific standing (System Admin, Tenant Admin for the relevant tenant, or simply "authenticated") before it is allowed to execute.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the operations described in the Assumptions below — including the System-Admin bypass of the per-tenant membership check — remain restricted to exactly the same standing after the migration, verified individually per operation (not only as an aggregate pass/fail) with zero regressions across the existing authorization-focused test suite.
- **SC-002**: For any protected operation, the required standing can be identified by inspecting the declaration attached to that operation itself, without consulting a separate registry and without reading into that operation's implementation logic.
- **SC-003**: Adding a new admin-protected operation in the future requires no new *per-operation* conditional or database-query code to be written — reusing a shared, already-existing permission check by declaring it (even if that shared check itself queries the database) satisfies this; only a one-off, operation-specific lookup would not.
- **SC-004**: The last-System-Admin safeguard continues to hold in 100% of attempts to remove the platform's final System Admin.

## Assumptions

- This migration preserves the existing two-tier admin model (System Admin as a platform-wide standing, Tenant Admin as a per-tenant-membership standing) — no new admin tiers are introduced by this feature. The word "Privileges" in this feature's originating request refers to Spring Security's generic term for a scoped permission (what this spec calls Tenant Admin standing — a check scoped to one resource) as distinct from a "Role" (System Admin standing — a platform-wide grant), not a request for additional, finer-grained permission types beyond these two.
- This feature deliberately reverses the previous role-system feature's (`003-rbac-user-roles`) explicit choice to keep admin checks as manual, per-service code rather than adopt a declarative authorization framework mechanism. That earlier choice was made to match the pattern already used for other business rules at the time; it is superseded here by an explicit request to centralize admin verification, closing the risk that a newly added operation silently omits its required check.
- For operations scoped to a specific tenant, the existing mechanism for resolving which tenant is being acted on (e.g., a route parameter or an active-tenant header) is reused unchanged; only how the actor's admin standing is checked against that tenant is affected. Operations with no tenant-scoping at all (listing/creating tenants platform-wide, granting or revoking System Admin standing) have no tenant-resolution step to begin with and are unaffected by this point.
- Authorization is re-evaluated on every request, consistent with the platform's current stateless, per-request authentication model; no new token-invalidation or session-revocation flow is introduced by this feature.
- All operations currently gated by manual System Admin or Tenant Admin checks — including tenant management, tenant membership management, System Admin standing grant/revoke, and the System-Admin bypass of per-tenant membership checks — are in scope for this migration.
- Operations that only require an authenticated user (no admin standing) are out of scope; this feature only touches operations that currently require admin standing.
- "Auditable" (User Story 2) refers to a human being able to statically discover an operation's required standing by inspecting its declaration — it does not introduce runtime audit logging of individual allow/deny decisions; logging denied attempts is out of scope for this feature.
- This migration is delivered as a single, atomic change covering every operation listed above together; it does not require or anticipate a partial/incremental rollout where some operations use the old mechanism and others use the new one at the same time.
- The per-request lookups this migration introduces are expected to be negligible in cost (single indexed primary-key or existing-query lookups, replacing lookups that were previously done redundantly) and are not expected to introduce measurable latency or require new performance testing.
