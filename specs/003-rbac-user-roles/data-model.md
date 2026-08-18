# Phase 1 Data Model: Role-Based Access for Users

Extends three existing entities (`AppUser`, `UserTenantMembership`, `Tenant`)
from `001-pessoa-beneficiario-crud`; introduces no new tables. Field names
below are the JPA/Java-side names; `research.md` §1 has the column-level
rationale.

## Entity: AppUser (extended)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID, PK | unchanged |
| `username` | text, unique, not null | unchanged |
| `passwordHash` | text, not null | unchanged |
| `createdAt` | timestamptz, not null | unchanged |
| `isSystemAdmin` | boolean, not null, default `false` | **NEW.** Platform-wide flag, independent of any `UserTenantMembership` row (FR-001, FR-008). |

**Validation rules**:
- FR-011/FR-014: an update that would set the last remaining
  `isSystemAdmin = true` user to `false`, or delete that user's account,
  MUST be rejected. Enforced by locking and re-reading the *entire current
  set* of System Admin rows (`AppUserRepository.findAllSystemAdminsForUpdate()`,
  `SELECT ... FOR UPDATE`) and deriving the count from that set, in the same
  transaction as the write — not by locking only the target row, which would
  not prevent two concurrent revokes against two *different* admins from each
  reading a stale count (research.md §9).
- Only a caller who already has `isSystemAdmin = true` may change another
  user's — or their own — `isSystemAdmin` value (FR-014). Setting a value
  that already matches the current one is a no-op (FR-014).

## Entity: UserTenantMembership (extended)

| Field | Type | Rules |
|---|---|---|
| `id` (userId, tenantId) | composite PK | unchanged |
| `user` | FK → AppUser | unchanged |
| `tenant` | FK → Tenant | unchanged |
| `isTenantAdmin` | boolean, not null, default `false` | **NEW.** Per-membership flag — this specific user's standing for this specific tenant only (FR-001, FR-005(c)). |

**Validation rules**:
- FR-005(c)/FR-006: a caller may set `isTenantAdmin` on a membership row —
  including their own — only if the caller holds `isSystemAdmin = true`, OR
  holds `isTenantAdmin = true` on their *own* membership row for that same
  `tenantId`. No cross-tenant exercise of Tenant Admin standing.
- FR-005(c): the target membership row (`userId` + `tenantId`) MUST already
  exist — granting Tenant Admin standing never implicitly creates membership;
  a non-member target is rejected (404), distinct from FR-005(a)'s separate
  add-membership action. Setting a value that already matches the current one
  is a no-op.
- Removing a membership row (FR-005(a)) removes any `isTenantAdmin` standing
  that came with it — there is no orphaned admin flag without a membership.
  A concurrent grant/revoke of `isTenantAdmin` racing against a membership
  removal is safe either way: both actions converge on "no membership, no
  standing" (spec.md Assumptions).
- No minimum-count protection is required for `isTenantAdmin` (unlike
  `isSystemAdmin`, see FR-011): a tenant may legitimately have zero Tenant
  Admins, since System Admin already has every Tenant Admin capability
  platform-wide (FR-008) and can always step in (spec.md Assumptions).

## Entity: Tenant (extended — now mutable/deletable, was previously seed-only/immutable)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID, PK | unchanged |
| `nome` | text, unique, not null | unchanged shape; **now updatable** (FR-005(b)) where it was previously immutable seed data |
| `createdAt` | timestamptz, not null | unchanged |

**Validation rules**:
- FR-002/FR-004: create and delete are restricted to callers with
  `isSystemAdmin = true`.
- FR-004/FR-005(b): update (`nome`) is allowed for callers with
  `isSystemAdmin = true`, or `isTenantAdmin = true` on their membership for
  *this* tenant.
- FR-003: delete is blocked (400, generic message) while
  `existsByTenant_Id(tenantId)` is true on either `BeneficiarioRepository` or
  `UserTenantMembershipRepository` — mirrors `001`'s Pessoa-deletion-block
  pattern exactly (see `research.md` §8).
- `nome` uniqueness constraint (already existing, unchanged) still applies to
  updates, not just creates.

## Derived/response shapes (no new tables)

- **UserProfile** (`GET /api/me`, extended): adds `isSystemAdmin: boolean`.
- **TenantSummary** (nested in `UserProfile.tenants`, extended): adds
  `isTenantAdmin: boolean` — the caller's own standing for that tenant.
- **MemberResponse** (new, `GET /api/tenants/{id}/members`): `userId`,
  `username`, `isTenantAdmin` — another member's standing within the tenant
  being viewed (only visible to a caller who already has access to that
  listing, i.e., System Admin or that tenant's own Tenant Admin).
- **UserSummary** (new, `GET /api/users?username=`): `id`, `username` only —
  intentionally minimal (research.md §6).

## State transitions

- A user's `isSystemAdmin` flag: `false` → `true` (grant, FR-014) and
  `true` → `false` (revoke, FR-014, blocked if last one — FR-011). No other
  states.
- A membership's `isTenantAdmin` flag: `false` → `true` (grant, FR-005(c))
  and `true` → `false` (revoke, FR-005(c)), or removed entirely alongside the
  membership row itself (FR-005(a)). No minimum-count protection (see above).
- A `UserTenantMembership` row itself: created (FR-005(a), add), deleted
  (FR-005(a), remove) — unchanged lifecycle from `001`, just now mutable
  post-seed instead of fixed.

Every transition above takes effect on the affected user's very next request
(FR-015) — enforced automatically by the fresh-lookup-per-request design in
`research.md` §2, not by any additional invalidation step.
