# Data Model: Migrate Admin Role Verification to Spring Security Authorization

## Persisted data

No schema change. This feature reuses the two boolean flags added in `003-rbac-user-roles`
without altering their shape, meaning, or storage:

- **`app_user.is_system_admin`** (`AppUser.isSystemAdmin`) — platform-wide System Admin standing.
- **`user_tenant_membership.is_tenant_admin`** (`UserTenantMembership.isTenantAdmin`) — Tenant
  Admin standing, scoped to that one membership row (one user, one tenant).

## Runtime authorization model (new)

Nothing below is persisted — these are request-scoped constructs computed fresh on every request
(per FR-004 and the Clarifications session), replacing the manual per-call database checks:

| Construct | Kind | Computed by | Fed by |
|---|---|---|---|
| `ROLE_SYSTEM_ADMIN` | `GrantedAuthority` on the request's `Authentication` | `JwtAuthenticationFilter` | `AppUserRepository.findById(userId).map(AppUser::isSystemAdmin)` |
| `TenantAuthorization.isTenantAdmin(UUID tenantId)` | `@Component` method, invoked from `@PreAuthorize` SpEL | `TenantAuthorization` | `UserTenantMembershipRepository.existsByUser_IdAndTenant_IdAndIsTenantAdminTrue(userId, tenantId)` |

Both read the caller's id from `SecurityContextHolder`'s current `Authentication`
(`JwtService.JwtPrincipal.userId()`), exactly as the manual checks did today — only *where* the
check is declared changes, not what it queries.

## Protected Operations (source of truth for this migration)

Every row is a currently-enforced admin check. `Required standing` and behavior are unchanged
after migration (FR-007); only the `New mechanism` column changes. This table is what
`/speckit-tasks` should use to enumerate the migration work, and what regression tests validate
against (SC-001).

| # | Operation | Method | Required standing | Current mechanism | New mechanism | Spec FR |
|---|---|---|---|---|---|---|
| 1 | List all tenants | `TenantService.list` | System Admin | `requireSystemAdmin` | `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` | FR-002 |
| 2 | Create tenant | `TenantService.create` | System Admin | `requireSystemAdmin` | `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` | FR-002 |
| 3 | Get tenant | `TenantService.get` | System Admin OR Tenant Admin of `tenantId` | `requireSystemAdminOrTenantAdmin` | `@PreAuthorize("hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)")` | FR-002, FR-003 |
| 4 | Update tenant | `TenantService.update` | System Admin OR Tenant Admin of `tenantId` | `requireSystemAdminOrTenantAdmin` | same expression as #3 | FR-002, FR-003 |
| 5 | Delete tenant | `TenantService.delete` | System Admin | `requireSystemAdmin` | `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` | FR-002 |
| 6 | List tenant members | `MembershipService.listMembers` | System Admin OR Tenant Admin of `tenantId` | `requireStandingFor` | same expression as #3 | FR-002, FR-003 |
| 7 | Add tenant member | `MembershipService.addMember` | System Admin OR Tenant Admin of `tenantId` | `requireStandingFor` | same expression as #3 | FR-002, FR-003 |
| 8 | Remove tenant member | `MembershipService.removeMember` | System Admin OR Tenant Admin of `tenantId` | `requireStandingFor` | same expression as #3 | FR-002, FR-003 |
| 9 | Grant Tenant Admin | `MembershipService.grantTenantAdmin` | System Admin OR Tenant Admin of `tenantId` | `requireStandingFor` | same expression as #3 | FR-002, FR-003 |
| 10 | Revoke Tenant Admin | `MembershipService.revokeTenantAdmin` | System Admin OR Tenant Admin of `tenantId` | `requireStandingFor` | same expression as #3 | FR-002, FR-003 |
| 11 | Grant System Admin | `AppUserService.grantSystemAdmin` | System Admin | `requireSystemAdmin` | `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` | FR-002 |
| 12 | Revoke System Admin | `AppUserService.revokeSystemAdmin` | System Admin (+ untouched last-admin invariant, see research.md §7) | `requireSystemAdmin` | `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` | FR-002, FR-006 |
| 13 | Beneficiário tenant-membership bypass | `TenantContextFilter` | System Admin (bypasses the membership-existence requirement) | direct `AppUserRepository` lookup | reads the `ROLE_SYSTEM_ADMIN` authority already on the request (research.md §5) | FR-002 |

Every row above is also covered by FR-001 (single declarative mechanism), FR-004 (fresh-per-request
evaluation), FR-005 (denial response shape), FR-007 (behavior parity), FR-008 (single declaration
per operation), FR-010 (no bleed-over to unprotected sibling operations), and SC-001 (each row
verified individually, not just in aggregate) — these apply uniformly across all 13 rows rather
than to any one row individually.

## Out of scope (explicitly unchanged)

- The last-System-Admin invariant (`AppUserService.revokeSystemAdmin`'s pessimistic lock) — a
  data-integrity rule, not an admin-standing check (research.md §7, spec FR-006).
- `TenantContextFilter`'s membership-existence rejection (missing `X-Tenant-Id` membership) — a
  data-isolation concern (Principle I), not an admin-standing check.
- Any endpoint that only requires an authenticated user (spec FR-009).
