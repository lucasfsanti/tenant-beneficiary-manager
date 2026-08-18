# Quickstart: Validate Role-Based Access for Users

Validates the feature against `spec.md`'s User Stories and Success Criteria.
References `data-model.md` for field details and `contracts/openapi.yaml` for
exact request/response shapes — not duplicated here. Assumes the seeded demo
accounts from `research.md` §7: `admin`/`demo123` (System Admin, no tenant
memberships), `bruno`/`demo123` (Tenant Admin of Tenant Alfa only), `ana`/`demo123`
(Normal tier, member of both Tenant Alfa and Tenant Beta).

## Prerequisites

- `docker-compose up` running (or `mvn test` for the automated suite)
- All three seeded accounts above present (per the new Liquibase changeset)

## Scenario 1 — System Admin manages tenants (US1, SC-001, SC-002)

1. Log in as `admin`.
2. `POST /api/tenants` with a new name → expect `201`, tenant appears in
   `GET /api/tenants`.
3. `PUT /api/tenants/{id}` to rename it → expect `200`, change reflected.
4. `DELETE /api/tenants/{id}` on this new, empty tenant → expect `204`.
5. `DELETE /api/tenants/{tenant-alfa-id}` (has Beneficiário/membership data) →
   expect `400` with a clear explanation (FR-003).
6. Log in as `ana` (no elevated standing) and repeat steps 2–4 → expect `403`
   on every attempt (SC-002).

## Scenario 2 — System Admin grants/revokes System Admin standing (US1, SC-007, SC-008, SC-009)

1. As `admin`, `PUT /api/users/{ana-id}/system-admin` → expect `204`; `ana`
   now has `isSystemAdmin: true` in `GET /api/me`.
2. As `admin`, `DELETE /api/users/{admin-id}/system-admin` (revoking
   themselves, now that `ana` is also a System Admin) → expect `204` (more
   than one System Admin exists, so this is allowed).
3. As `ana` (now the only System Admin), attempt
   `DELETE /api/users/{ana-id}/system-admin` (self-revoke, last one) →
   expect `400`, blocked (FR-011, SC-007).
4. As `bruno` (no System Admin standing), attempt to grant/revoke anyone's
   System Admin standing → expect `403` (SC-009).
5. As `ana`, `PUT /api/users/{ana-id}/system-admin` again (already held) →
   expect `204`, no error (FR-014 idempotency).
6. As `ana`, re-grant `admin`'s System Admin standing (`PUT
   /api/users/{admin-id}/system-admin`) — restores the seed baseline. Using
   `admin`'s original, still-unexpired token from step 1 of this scenario
   (never re-logged-in since being revoked), immediately issue any
   System-Admin-only request as `admin` → expect it to succeed without
   re-login (FR-015, SC-010) — access follows fresh standing, not a stale
   token claim.

**Note on concurrency (FR-011)**: the "two concurrent revokes against the last
two System Admins can't both succeed" guarantee (research.md §9) is not
practically reproducible via manual curl steps — it's validated by a
dedicated integration test issuing both requests from parallel threads
against a two-System-Admin fixture, not exercised in this manual walkthrough.

## Scenario 3 — Tenant Admin manages their own tenant's membership and details (US2, SC-003, SC-004)

1. Log in as `bruno` (Tenant Admin of Tenant Alfa).
2. `GET /api/tenants/{alfa-id}/members` → expect `200`, listing shows `bruno`
   with `isTenantAdmin: true`.
3. `GET /api/users?username=ana` → note `ana`'s id, then
   `POST /api/tenants/{alfa-id}/members` with that id → expect `201`; `ana`
   already belonged, this call is idempotent/confirms existing behavior — use
   a fresh, not-yet-member seeded user id if the environment has one, or skip
   to step 4 if not.
4. `PUT /api/tenants/{alfa-id}` renaming it → expect `200`.
5. `PUT /api/tenants/{alfa-id}/members/{ana-id}/tenant-admin` → expect `204`;
   `ana` now `isTenantAdmin: true` for Tenant Alfa only. As `ana`, her very
   next request (e.g. `GET /api/me`) already reflects it, no re-login (FR-015).
6. `DELETE /api/tenants/{alfa-id}/members/{ana-id}/tenant-admin` → expect
   `204`; reverts. Repeat once more (already not held) → expect `204` again,
   no error (FR-005(c) idempotency).
7. `PUT /api/tenants/{alfa-id}/members/{beta-only-user-id}/tenant-admin` for a
   user who is not a member of Tenant Alfa → expect `404`, and confirm no
   membership was created as a side effect (User Story 2 scenario 10).
8. As `bruno`, `DELETE /api/tenants/{alfa-id}/members/{bruno-id}/tenant-admin`
   (revoking his own Tenant Admin standing) → expect `204`; `bruno` is now a
   Normal-tier member of Tenant Alfa (no last-Tenant-Admin protection — User
   Story 2 scenario 12). Restore it afterward (as `admin`) so later steps and
   Scenario 4 can still use `bruno` as the Tenant Admin persona.
9. As `bruno` (Tenant Admin restored), attempt any of steps 2–8 against
   `{beta-id}` (Tenant Beta, where `bruno` has no membership at all) →
   expect `403` for each (SC-003).
10. As `bruno`, attempt `POST /api/tenants` or `DELETE /api/tenants/{alfa-id}` →
    expect `403` — create/delete remain System-Admin-exclusive even for a
    Tenant Admin of that tenant (User Story 1 scenario 6 / User Story 2
    scenario 6).

## Scenario 4 — Normal user baseline is unchanged (US3, SC-005)

1. Log in as `ana` (after Scenario 2/3's restores, back to Normal tier
   everywhere but Tenant Alfa's Tenant Admin flag if step 5 above wasn't
   reverted — revert it first).
2. Perform the existing Pessoa CRUD and Beneficiário CRUD flows from
   `specs/001-pessoa-beneficiario-crud/quickstart.md` unchanged → expect
   identical behavior to before this feature (zero regressions).
3. Attempt `POST /api/tenants` or `POST /api/tenants/{any-id}/members` →
   expect `403` (FR-010).

## Scenario 5 — Regression: existing isolation/CRUD suite still passes

```bash
cd backend && mvn test
```

Expect all pre-existing `001`/`002` integration tests to pass unchanged —
role tier is additive and must not alter Beneficiário/Pessoa behavior for
Normal-tier users (SC-005).

## Cleanup

Revert any grant/revoke or rename performed above that isn't already
reverted within its own scenario, so the seed baseline (`admin` = System
Admin only, `bruno` = Tenant Admin of Alfa only, `ana` = Normal tier
everywhere) is restored for the next run.
