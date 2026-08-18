# Quickstart: Migrate Admin Role Verification to Spring Security Authorization

Validates that admin verification now runs through Spring Security's declarative authorization
and that behavior is unchanged from before the migration (FR-007/SC-001).

## Prerequisites

- Full stack running: `docker-compose up` from the repo root (PostgreSQL + backend + frontend).
- Seed data from `003-rbac-user-roles` present: at least one seeded System Admin user, one Tenant
  Admin (for a specific seeded tenant), and one Normal user, per the existing seed changeset.
- `curl` and `jq` (or any HTTP client) for the manual checks below.

## 1. Automated regression pass (primary validation)

```bash
cd backend
./mvnw test -Dtest=TenantCrudTest,SystemAdminGrantRevokeTest,SystemAdminConcurrentRevokeTest,\
TenantAdminGrantRevokeTest,TenantUpdateAuthorizationTest,NormalUserRoleBaselineTest,\
SystemAdminBeneficiarioAccessTest,MembershipManagementTest,TenantMembershipEnforcementTest
```

**Expected**: All tests pass, unmodified in outcome — same 200/403 assertions as before the
migration (SC-001). This is the authoritative check; the manual steps below are for spot-checking
the one behavior this migration specifically changes the *mechanism* for: revocation freshness.

## 2. Static coverage check (SC-002/FR-008)

Open `data-model.md`'s Protected Operations table side-by-side with
`TenantService.java`, `MembershipService.java`, and `AppUserService.java`. For each of the 12
service methods, confirm the `@PreAuthorize` annotation on the method signature matches the
table's `New mechanism` column — this should be verifiable without reading into any method body.

## 3. Manual revocation-freshness check (Clarifications 2026-08-18, FR-004)

1. Log in as a System Admin (`POST /api/auth/login`) and capture the token.
2. Using that token, grant System Admin standing to a second, currently-Normal user:
   `PUT /api/users/{userId}/system-admin`.
3. Log in as that second user and capture *their* token — this token stays valid for the rest of
   this check.
4. Confirm the second user can now list tenants: `GET /api/tenants` with their token → expect
   `200`.
5. Using the original System Admin's token, revoke the second user's standing:
   `DELETE /api/users/{userId}/system-admin`.
6. Immediately reuse the second user's *original, still-unexpired* token:
   `GET /api/tenants` → expect `403` with a `ProblemDetail` body
   (`"title": "Acesso negado"`), **not** a stale `200`. This is the behavior the Clarifications
   session locked in: revocation takes effect on the very next request, not on next login.

## 4. Manual Tenant Admin scoping check (spec Acceptance Scenario 2)

1. Log in as a user who is Tenant Admin of Tenant A only.
2. `PUT /api/tenants/{tenantIdA}` with a body changing the name → expect `200`.
3. `PUT /api/tenants/{tenantIdB}` (a different tenant they don't administer) with the same body
   shape → expect `403`.

## 5. Last-System-Admin safeguard check (spec User Story 3, unaffected by this migration)

1. Ensure the seeded data has exactly one System Admin (or reduce to one via the grant/revoke
   endpoints as System Admin).
2. As that sole System Admin, attempt `DELETE /api/users/{ownUserId}/system-admin` on themself →
   expect `400` (`BusinessRuleException` → RFC 7807, unchanged by this migration) with a message
   indicating at least one System Admin must remain.
