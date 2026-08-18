# Implementation Plan: Role-Based Access for Users

**Branch**: `003-rbac-user-roles` | **Date**: 2026-08-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-rbac-user-roles/spec.md`

## Summary

Add three permission tiers — System Admin (platform-wide), Tenant Admin
(per tenant membership), Normal user (today's existing baseline, unchanged)
— on top of the existing `app_user` / `user_tenant_membership` tables. New
capabilities: System Admin gets full Tenant CRUD (net-new; tenants are
currently seed-only) and can grant/revoke System Admin standing; a Tenant
Admin can add/remove members of their own tenant, edit that tenant's own
attributes, and grant/revoke Tenant Admin standing for peers in that same
tenant. All authorization checks are resolved fresh from the database on
every request (mirroring the existing `TenantContextFilter` pattern), never
trusted from JWT claims, so a promotion/demotion takes effect immediately
without requiring re-login. No new dependencies; extends the existing
Spring Boot service-layer business-rule pattern (manual checks + typed
exceptions → RFC 7807), not Spring Method Security annotations.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3.4) · JavaScript (ES2022) for the Vue 3 front-end — unchanged from `001-pessoa-beneficiario-crud`

**Primary Dependencies**: No new dependencies. Reuses Spring Data JPA, Spring
Security (JWT, stateless), Liquibase (schema migrations, per
`002-liquibase-migration`), springdoc-openapi, Jakarta Bean Validation; Vue 3
(Composition API) + Pinia + Axios on the front-end

**Storage**: PostgreSQL 16, same shared database/schema. Two new columns on
existing tables (`app_user.is_system_admin`, `user_tenant_membership.is_tenant_admin`)
via a new Liquibase changeset — no new tables

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL), same
pattern as `001` — new integration tests for Tenant CRUD, membership
management, and both grant/revoke flows, including a dedicated last-admin
protection test and a regression pass confirming the existing isolation/CRUD
test suite is unaffected by role tier; Vitest for new front-end components

**Target Platform**: Linux containers via Docker Compose — unchanged

**Project Type**: Web application (backend + frontend), touches both

**Performance Goals**: No strict SLA; same demo/review scale as `001`

**Constraints**: Every authorization decision (System Admin standing, Tenant
Admin standing for a specific tenant) MUST be resolved by a fresh repository
lookup on each request — never cached in or trusted from the JWT — because
standing can change at runtime via this feature's own grant/revoke endpoints
and the existing JWT expiration (480 minutes) is far longer than an
admin action should take to become effective; error responses MUST remain
RFC 7807 (Principle II); every new endpoint MUST be documented via
OpenAPI/Swagger (Principle III); Beneficiário cross-tenant isolation
(Principle I) MUST hold unchanged — the new System Admin platform-wide reach
is an administrative-capability exception the spec explicitly grants, not a
weakening of per-record isolation, since System Admin still goes through the
same tenant-scoped repository methods as everyone else, just permitted to
select any tenant

**Scale/Scope**: 2 new columns on existing tables; ~13 new/changed REST
endpoints (Tenant CRUD, tenant membership add/remove, Tenant Admin grant/
revoke, System Admin grant/revoke, a minimal user lookup endpoint); 3 new
front-end views/components (Tenant management list/form for System Admin,
a tenant-member management panel for Tenant Admin, a System Admin roster
panel) plus role-aware conditional UI in the existing shell; seed data
gains one new System Admin account and promotes one existing seeded
membership to Tenant Admin

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. Multitenant Data Isolation (NON-NEGOTIABLE) | PASS | New membership/Tenant-Admin-grant endpoints take `tenantId` from the URL path and re-validate the caller's standing (System Admin, or Tenant Admin of *that* tenant id) via a fresh repository lookup before any write — the same centralized-enforcement spirit as `TenantContextFilter`, just implemented as a service-layer check since these endpoints are addressed by tenant id in the path rather than an "active tenant" header. Beneficiário isolation itself is untouched: System Admin's platform-wide reach only affects which tenant it may *select*, not how Beneficiário queries are scoped once selected. |
| II. Data Integrity & Explicit Validation | PASS | New business rules (last-System-Admin protection, Tenant-deletion referential block, role-mismatch denials) are server-side service-layer checks throwing typed exceptions, mapped to RFC 7807 via a new `ForbiddenException` → 403 handler (`ApiExceptionHandler`), consistent with the existing `NotFoundException`/`ConflictException`/`BusinessRuleException` pattern. |
| III. API Contract Documentation | PASS | New endpoints get springdoc-openapi annotations same as existing controllers; design-time contract captured in `contracts/openapi.yaml` (new endpoints only — `001`'s contract is untouched). |
| IV. Reproducible, Zero-Touch Environment | PASS | New Liquibase changeset (schema + seed) runs automatically on `docker-compose up`, per the pattern `002` established; no manual step. |
| V. Simplicity & Justified Technology Choices | PASS | No new dependency, no new framework mechanism (no `@EnableMethodSecurity`/`@PreAuthorize`, which the codebase has never used) — role checks follow the exact same "manual check in service layer, throw typed exception" pattern already used for CPF/matrícula/Pessoa-deletion business rules. |
| Technology Stack & Persistence | PASS | Same stack throughout; no deviation. |
| Delivery & Documentation Requirements | PASS (tracked) | README's architecture section will need a short addition describing the role model — flagged here so `/speckit-tasks` includes it. |

No violations requiring justification — Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-rbac-user-roles/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── openapi.yaml     # New/changed endpoints only
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/tbm/
│   ├── tenant/
│   │   ├── Tenant.java                  # + no shape change (still id/nome/created_at)
│   │   ├── TenantRepository.java        # unchanged — delete-block check uses
│   │   │                                #   BeneficiarioRepository/UserTenantMembershipRepository instead
│   │   ├── TenantService.java           # NEW — create/list/get/update/delete + role checks
│   │   ├── TenantController.java        # NEW — /api/tenants CRUD
│   │   ├── MembershipService.java       # NEW — add/remove member, grant/revoke Tenant Admin
│   │   ├── MembershipController.java    # NEW — /api/tenants/{id}/members/**
│   │   └── dto/
│   │       ├── TenantInput.java         # NEW
│   │       ├── TenantResponse.java      # NEW
│   │       ├── MemberResponse.java      # NEW — user id/username + tier within this tenant
│   │       └── AddMemberRequest.java    # NEW — { userId }
│   ├── user/
│   │   ├── AppUser.java                 # + isSystemAdmin field
│   │   ├── AppUserRepository.java       # + countByIsSystemAdminTrue, existence/lookup helpers
│   │   ├── UserTenantMembership.java    # + isTenantAdmin field
│   │   ├── UserTenantMembershipRepository.java  # + admin-flag query methods
│   │   ├── UserAdminController.java     # NEW — /api/users/{id}/system-admin grant/revoke
│   │   ├── UserController.java          # NEW — GET /api/users?username= (member-picker lookup)
│   │   ├── AuthService.java             # buildProfile() extended with role flags
│   │   └── dto/
│   │       ├── UserProfile.java         # + isSystemAdmin
│   │       ├── TenantSummary.java       # + isTenantAdmin
│   │       └── UserSummary.java         # NEW — id/username, for the member picker
│   └── common/exception/
│       └── ForbiddenException.java      # NEW — maps to 403 ProblemDetail
├── src/main/resources/db/changelog/
│   ├── db.changelog-master.yaml         # + include for 003-role-system.sql
│   └── 003-role-system.sql              # NEW changeset: 2 columns + seed updates
└── src/test/java/com/tbm/integration/
    ├── TenantCrudTest.java              # NEW
    ├── TenantDeletionRestrictionTest.java  # NEW
    ├── MembershipManagementTest.java    # NEW
    ├── TenantAdminGrantRevokeTest.java  # NEW
    └── SystemAdminGrantRevokeTest.java  # NEW (incl. last-admin protection)

frontend/
├── src/
│   ├── views/
│   │   ├── TenantListView.vue           # NEW — System Admin: list/create/delete tenants
│   │   ├── TenantFormView.vue           # NEW — System Admin or Tenant Admin: edit tenant + members
│   │   └── SystemAdminsView.vue         # NEW — System Admin: grant/revoke System Admin standing
│   ├── stores/
│   │   ├── auth.js                      # + isSystemAdmin, per-tenant isTenantAdmin from /api/me
│   │   └── tenant.js                    # NEW — Pinia store for tenant/membership admin actions
│   ├── services/
│   │   └── tenantAdminApi.js            # NEW — tenants, members, both grant/revoke endpoints
│   └── router/index.js                  # + role-gated routes (requiresSystemAdmin / requiresTenantAdmin)
└── tests/unit/
    └── TenantListView.spec.js           # NEW (representative; full list in tasks.md)
```

**Structure Decision**: Same two-project layout as `001`/`002`
(`backend/`, `frontend/`), no new top-level directories. New backend code
lives in the existing `tenant` and `user` packages (matching where `Tenant`/
`AppUser`/`UserTenantMembership` already live) rather than a new `rbac`
package, since this feature extends those entities rather than introducing
an independent domain. New front-end views follow the existing
`views`/`stores`/`services` split.

## Post-Design Constitution Check

*Re-evaluated after Phase 1 (data-model.md, contracts/openapi.yaml, quickstart.md).*

All seven rows of the Constitution Check table above still PASS with no
changes: the fresh-lookup-per-request authorization design (research.md §2)
keeps Principle I's centralized-enforcement spirit intact for the new
tenant-scoped endpoints; the new `ForbiddenException`→403 mapping
(research.md §4) slots into the existing `ApiExceptionHandler` RFC 7807
pattern without altering it; `contracts/openapi.yaml` documents every new
endpoint; the new Liquibase changeset (`003-role-system.sql`, data-model.md)
follows `002`'s established migration approach; no new dependency or
authorization framework was introduced (research.md §3). No new complexity or
deviation was introduced during design.

## Complexity Tracking

*No violations — table not needed.*
