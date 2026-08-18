# Phase 0 Research: Role-Based Access for Users

## 1. Where role standing is stored

**Decision**: `app_user.is_system_admin BOOLEAN NOT NULL DEFAULT false` for
System Admin (platform-wide, one flag per user). `user_tenant_membership.is_tenant_admin
BOOLEAN NOT NULL DEFAULT false` for Tenant Admin (one flag per membership
row, so a user can hold it for one tenant and not another).

**Rationale**: Directly matches the spec's FR-001 (System Admin platform-wide,
Tenant Admin per-membership) and Key Entities section. No new tables needed —
both existing tables already carry exactly the right cardinality (one row per
user for `app_user`, one row per user-tenant pair for `user_tenant_membership`).

**Alternatives considered**: A separate `role` table with a `user_id`,
`tenant_id NULLABLE`, `role_type` enum — more "properly normalized" and
extensible to future role types, but introduces a table and query joins for
zero present benefit (the spec defines exactly two flags, not an open-ended
role catalog); rejected per Constitution Principle V (no speculative
abstraction beyond present scope).

## 2. How authorization is enforced (fresh-lookup vs. JWT claims)

**Decision**: Never encode `isSystemAdmin` or `isTenantAdmin` in the JWT as
the source of truth for authorization. Every service method that requires
elevated standing performs a fresh repository lookup (`AppUserRepository`/
`UserTenantMembershipRepository`) keyed by the authenticated principal's
`userId` (from `JwtService.JwtPrincipal`, itself unchanged) at call time.

**Rationale**: The codebase already establishes this exact precedent —
`TenantContextFilter` re-validates tenant membership against the database on
every request rather than trusting the JWT's informational `tenantIds` claim,
specifically so that membership changes take effect without requiring
re-login. Role standing has the identical staleness risk (this feature's own
grant/revoke endpoints can change it mid-session), and the JWT's 480-minute
expiration (`app.jwt.expiration-minutes`) is far longer than an admin action
should take to become effective. Reusing the established pattern also avoids
introducing a second, inconsistent authorization mechanism (Constitution
Principle V).

**Alternatives considered**: Embedding role flags in the JWT and requiring
re-login after any grant/revoke — rejected as a worse user experience with no
offsetting benefit, and inconsistent with how membership changes already work
today (immediate, not requiring re-login, per `001` FR-018/SC-004's spirit,
now spec.md SC-004 for this feature). Caching role lookups in-request only
(no cross-request cache) was considered and is effectively what "fresh
repository lookup per service call" already does — no separate caching layer
is introduced.

## 3. How role checks are implemented (manual checks vs. Spring Method Security)

**Decision**: Manual checks inside service methods (e.g.,
`TenantService.update()` checks `isSystemAdmin(callerId) ||
isTenantAdminFor(callerId, tenantId)` before proceeding, else throws a new
`ForbiddenException`), not `@PreAuthorize`/`@EnableMethodSecurity`.

**Rationale**: The codebase has zero existing usage of Spring Method
Security anywhere — `SecurityConfig` only does request-matcher-based
`authorizeHttpRequests` (authenticated-or-not), and every existing business
rule (CPF uniqueness, matrícula uniqueness, Pessoa-deletion-block,
tenant-membership validation in `TenantContextFilter`) is a manual check in a
service or filter, throwing a typed exception mapped to RFC 7807 by
`ApiExceptionHandler`. Introducing method-security annotations for this one
feature would add a second, parallel authorization idiom for no material
benefit at this project's scale (a handful of role-gated methods),
contradicting Principle V.

**Alternatives considered**: `@PreAuthorize("@authz.isSystemAdmin()")` style
SpEL expressions — more declarative, but requires wiring
`@EnableMethodSecurity`, a custom bean, and SpEL expressions the rest of the
codebase has no precedent for; rejected as unnecessary machinery for ~6
role-gated methods.

## 4. New exception type for 403 responses

**Decision**: Add `com.tbm.common.exception.ForbiddenException`, handled by a
new `@ExceptionHandler` in `ApiExceptionHandler` mapping to
`HttpStatus.FORBIDDEN` with title "Acesso negado" (matching the existing
Portuguese-title convention and the exact wording `TenantContextFilter`
already uses for its own 403s).

**Rationale**: The existing exception set (`BusinessRuleException`→400,
`NotFoundException`→404, `ConflictException`→409, `UnauthorizedException`→401)
has no 403 case — every existing 403 today is hand-written inline inside
`TenantContextFilter` because that check happens before Spring Security's
authorization context is fully usable in a controller/service. This
feature's role checks happen inside services (after authentication and, where
relevant, tenant-context resolution), so the standard
exception-thrown-in-service → `@RestControllerAdvice`-mapped pattern applies
cleanly, consistent with how every other business-rule violation in this
codebase is already surfaced.

**Alternatives considered**: Reusing `BusinessRuleException` (400) for role
denials — rejected because 403 Forbidden is the semantically correct status
for "authenticated but not permitted," and the spec's FR-013 promises "a
clear, specific error," which a misleading 400 would undermine.

## 5. Endpoint shape for Tenant, membership, and both grant/revoke flows

**Decision**:
- `POST /api/tenants`, `GET /api/tenants`, `GET /api/tenants/{id}`,
  `PUT /api/tenants/{id}`, `DELETE /api/tenants/{id}` — Tenant CRUD.
- `GET /api/tenants/{id}/members`, `POST /api/tenants/{id}/members`
  (body: `{ userId }`), `DELETE /api/tenants/{id}/members/{userId}` —
  membership management.
- `PUT /api/tenants/{id}/members/{userId}/tenant-admin`,
  `DELETE /api/tenants/{id}/members/{userId}/tenant-admin` — Tenant Admin
  standing grant/revoke, scoped to tenant `{id}`.
- `PUT /api/users/{userId}/system-admin`,
  `DELETE /api/users/{userId}/system-admin` — System Admin standing
  grant/revoke, platform-wide (System Admin only).
- `GET /api/users?username=` — minimal existing-user lookup by exact
  username, used by the "add member" picker (User Story 2 scenario 1 requires
  referencing an *existing* user).

**Rationale**: Follows the same plain-REST, resource-nested style already
used by `PessoaController`/`BeneficiarioController` (no RPC-style action
verbs in the main resource paths). Nesting members and Tenant Admin standing
under `/api/tenants/{id}/...` makes the tenant-scoping explicit in the URL
itself (unlike Beneficiário's `X-Tenant-Id` header pattern, these operations
are inherently *about* a specific tenant chosen by the caller — an admin
managing Tenant X, not "my currently active tenant" — so a header would be
the wrong mechanism here). System Admin grant/revoke is deliberately under
`/api/users/{userId}/`, not `/api/tenants/...`, since it is platform-wide and
has nothing to do with any specific tenant.

**Alternatives considered**: A single generic
`PATCH /api/tenants/{id}/members/{userId}` with a body describing the desired
role — more flexible, but obscures the two genuinely different actions
(membership existence vs. admin-standing flag) behind one endpoint and
complicates the RFC 7807 error messaging (which failure is it?); rejected in
favor of explicit, single-purpose endpoints matching FR-005's three distinct
(a)/(b)/(c) capabilities.

## 6. `GET /api/users` exposure scope

**Decision**: Any authenticated user may call `GET /api/users?username=`
(exact-match lookup only, returning just `id`/`username`) — no additional
role gate on the lookup itself.

**Rationale**: Matches the existing security posture: `SecurityConfig`
currently gates everything on "authenticated or not," with finer-grained
business rules living in services, and usernames are not sensitive data (they
are already implicitly discoverable via the login form itself, and this
project's Assumptions have never treated them as secret). Gating a read-only,
exact-match lookup behind "caller holds Tenant Admin standing *somewhere*"
would require a new kind of check (not "for tenant X," but "for any tenant at
all") for no real security benefit, and only Tenant Admin/System Admin UI
would ever call it in practice.

**Alternatives considered**: Restricting to System Admin/any Tenant Admin —
rejected per the rationale above; adds complexity without closing a real
exposure (username enumeration risk is already effectively bounded by the
existing login endpoint's identical username-existence signal surface).

## 7. Seed data for demonstrating all three tiers (FR-012)

**Decision**: Reuse the existing seeded users where possible rather than
inventing new personas: `bruno`'s existing Tenant Alfa membership (already
the single-tenant demo user, per `001`'s FR-017 clarification) gains
`is_tenant_admin = true`, demonstrating Tenant Admin without adding a new
account. Add exactly one new seeded user, `admin` / `demo123`, with
`is_system_admin = true` and no tenant memberships (demonstrating that System
Admin standing needs none, per FR-008/spec.md Assumptions). `ana` is
unchanged (multi-tenant, Normal tier both places) — she remains the
Normal-user demonstration persona alongside `bruno`'s now-dual role
(Tenant Admin in Alfa, plain Normal-tier member of nothing else, since he's
single-tenant).

**Rationale**: Minimizes new seed surface while satisfying FR-012 (at least
one System Admin, at least one Tenant Admin, continued Normal-tier users, all
immediately available). Reusing `bruno` also means the existing
single-tenant-user rationale from `001` (demonstrating the FR-021/SC-009
cross-tenant denial path) and this feature's new Tenant Admin path share one
persona, keeping the demo login table in `README.md` short.

**Alternatives considered**: A dedicated `carla`/Tenant-Admin-only persona
distinct from `bruno` — considered for clarity, but rejected as unnecessary
seed bloat when `bruno` already fits the role without conflicting with any
existing test assertion (role tier is additive/orthogonal to the isolation
tests `001` already established).

## 8. Tenant deletion referential-safety check

**Decision**: `TenantService.delete()` checks
`beneficiarioRepository.existsByTenant_Id(tenantId) ||
membershipRepository.existsByTenant_Id(tenantId)` before deleting; if either
is true, throws `BusinessRuleException` ("still linked to at least one
Beneficiário record or user membership").

**Rationale**: Mirrors `PessoaService`'s existing delete-block pattern
exactly (existence-only query, generic message, `BusinessRuleException`→400
via `ApiExceptionHandler`), per FR-003 and the spec's explicit
referential-safety-over-cascading-delete Assumption.

**Alternatives considered**: Cascading delete (remove all Beneficiário
records and memberships along with the Tenant) — explicitly rejected by the
spec's Assumptions section, matching the platform's established stance.

## 9. Atomic last-System-Admin protection (FR-011)

**Decision**: `AppUserService.revokeSystemAdmin` reads the *entire current set*
of System Admin users via a locking query
(`@Lock(LockModeType.PESSIMISTIC_WRITE) SELECT u FROM AppUser u WHERE
u.isSystemAdmin = true`), derives the count from that in-memory set, and
performs the write inside the same `@Transactional` boundary — all before
releasing the locks. Locking only the *target* row is insufficient: two
concurrent revokes against two *different* admins would each lock a
different row and neither would block the other, so both could still read a
stale count. Locking the whole matching row set instead makes both calls
contend for at least one shared row whenever the admin count is small (which
it always is when close to the last-one boundary).

**Rationale**: FR-011 now explicitly requires this invariant to hold "even
under concurrent requests" (spec.md, resolved via the authorization
checklist). A plain read-then-write without locking has a TOCTOU race: two
requests could both read `count = 2`, both pass the ">= 1 remaining" check,
and both commit, leaving zero. PostgreSQL's `SELECT ... FOR UPDATE`
documented behavior — re-checking a blocked row's WHERE-clause membership
against its post-commit value once the blocking transaction commits, and
omitting it from the result set if it no longer matches — is what makes the
second caller observe the correct, post-revoke admin count rather than a
stale one. Pessimistic locking on the small, low-write `app_user` table has
negligible performance cost at this project's scale and requires no new
dependency.

**Alternatives considered**: Optimistic locking (version column, retry on
conflict) — works but adds retry-loop complexity for a rare, low-throughput
action (admin grant/revoke); a database-level `CHECK` constraint enforcing
"at least one row has `is_system_admin = true`" — not expressible as a
standard SQL table-level `CHECK` constraint (it's an aggregate/cross-row
condition), would require a trigger, which is more machinery than a
transactional locking read for the same guarantee.
