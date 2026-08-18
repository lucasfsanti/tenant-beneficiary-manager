# Implementation Plan: Migrate Admin Role Verification to Spring Security Authorization

**Branch**: `004-spring-security-admin-roles` | **Date**: 2026-08-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-spring-security-admin-roles/spec.md`

## Summary

Replace the 12 hand-written `if (!isSystemAdmin(...)) throw new ForbiddenException(...)`-style
checks spread across `TenantService`, `MembershipService`, and `AppUserService` (added in
`003-rbac-user-roles`) with declarative Spring Security method security. System Admin becomes a
global `ROLE_SYSTEM_ADMIN` `GrantedAuthority`, computed fresh from the database on every request
by `JwtAuthenticationFilter` (never cached in the JWT). Tenant Admin — inherently scoped to one
tenant, not global — becomes a small `@Component` bean method referenced from `@PreAuthorize` SpEL
(`hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)`). `TenantContextFilter`'s
own System-Admin bypass check is simplified to read the same authority instead of re-querying the
database. A new `@ExceptionHandler(AccessDeniedException.class)` method on the existing
`ApiExceptionHandler` maps the resulting `AccessDeniedException` to the exact RFC 7807 shape the
removed `ForbiddenException` used to produce, so no observable behavior changes for callers (a
filter-level `AccessDeniedHandler` on `SecurityConfig` was tried first but never actually runs —
see research.md §4). No schema change, no new dependency
(method security ships inside the already-present `spring-boot-starter-security`), no frontend
change.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3.4) — unchanged; this feature has no
front-end surface

**Primary Dependencies**: No new dependency. Enables `@EnableMethodSecurity` /
`@PreAuthorize`, part of `spring-security-config`, already transitively pulled in by the existing
`spring-boot-starter-security`; reuses existing Spring Data JPA repositories
(`AppUserRepository`, `UserTenantMembershipRepository`) and `ProblemDetail` for error responses

**Storage**: PostgreSQL 16, same schema — no new columns or tables. Reuses
`app_user.is_system_admin` and `user_tenant_membership.is_tenant_admin`, both added in
`003-rbac-user-roles`

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL). The existing
authorization-focused integration suite (`TenantCrudTest`, `SystemAdminGrantRevokeTest`,
`SystemAdminConcurrentRevokeTest`, `TenantAdminGrantRevokeTest`, `TenantUpdateAuthorizationTest`,
`NormalUserRoleBaselineTest`, `SystemAdminBeneficiarioAccessTest`, `MembershipManagementTest`,
`TenantMembershipEnforcementTest`, `TenantContextFilterTest`) is the primary regression gate for
FR-007/SC-001 — it must keep asserting the same 200/403 outcomes; only call sites that pass a
now-removed `callerId` argument need updating. No new test classes are required for the new
mechanism itself: these integration tests exercise real HTTP requests through the full filter
chain and controller/service layer, so they already black-box-test whichever mechanism sits
behind an endpoint — the new `JwtAuthenticationFilter` authority population, `TenantAuthorization`
bean, and `ApiExceptionHandler.handleAccessDenied` are exercised by the same 200/403 assertions
without needing dedicated new tests

**Target Platform**: Linux containers via Docker Compose — unchanged

**Project Type**: Backend-only. The front-end already treats any `403` as a generic
access-denied response and needs no change, since the response shape is unchanged

**Performance Goals**: No strict SLA. At most one extra indexed primary-key lookup per
authenticated request (in `JwtAuthenticationFilter`, to populate `ROLE_SYSTEM_ADMIN`) — this
replaces lookups that were previously done redundantly, once per admin-service call, so total
query volume for a typical admin request is expected to drop, not rise

**Constraints**: Per the Clarifications below and FR-004, authorization MUST be evaluated fresh
on every request and MUST NEVER be cached in or derived from the JWT itself — a revoked admin
must be blocked on their very next request even though the existing JWT (480-minute expiry,
`JwtService`) is still otherwise valid. 403 responses MUST remain RFC 7807 `ProblemDetail`,
textually equivalent to today's (Principle II). The last-System-Admin invariant enforced by
`AppUserService.revokeSystemAdmin`'s pessimistic lock is a data-integrity business rule, not an
authorization check, and stays untouched (FR-006, out of scope for this migration)

**Scale/Scope**: 12 service methods across 3 classes converted from manual checks to
`@PreAuthorize`; 1 filter (`TenantContextFilter`) simplified to reuse the new authority; 1 new
`@Component` (tenant-admin permission bean); 1 new `@ExceptionHandler` method on
`ApiExceptionHandler`; removal of `ForbiddenException` and its old `ApiExceptionHandler` mapping
(confirmed to have no other callers). 0 new REST endpoints, 0 schema changes, 0 front-end changes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. Multitenant Data Isolation (NON-NEGOTIABLE) | PASS | Beneficiário isolation itself is untouched — `TenantContextFilter` remains the single centralized enforcement point (Principle I). Its membership-existence check is unchanged; only its System-Admin *bypass* condition switches from its own direct repository lookup to reading the `ROLE_SYSTEM_ADMIN` authority that `JwtAuthenticationFilter` already computed for the same request, removing a duplicate query rather than adding a second enforcement path. |
| II. Data Integrity & Explicit Validation | PASS | 403 responses stay RFC 7807 `ProblemDetail` with an equivalent title/detail, now produced by a new `handleAccessDenied` method in the existing `ApiExceptionHandler` instead of its old `ForbiddenException` mapping — same shape, same file, same pattern as every other mapped exception, verified in research.md §4 (which also records why a filter-level `AccessDeniedHandler` was tried and rejected). |
| III. API Contract Documentation | PASS | No request/response shape, status code, or URL changes (FR-007); existing OpenAPI annotations and `003`'s `contracts/openapi.yaml` remain accurate as-is — see `contracts/README.md`. |
| IV. Reproducible, Zero-Touch Environment | PASS | No schema/migration change, no new manual setup step; `docker-compose up` behavior is unaffected. |
| V. Simplicity & Justified Technology Choices | PASS | No new dependency — method security is already part of the mandated `spring-boot-starter-security`. This does reverse `003`'s explicit choice to avoid `@PreAuthorize` ("no new framework mechanism... which the codebase has never used") — that choice was made for a different reason (matching the then-existing manual-check pattern) and is superseded here by an explicit user request to centralize admin verification, which directly removes the "a new endpoint can silently omit its admin check" risk the manual-check pattern carried. This is a deliberate, documented change of approach, not silent architecture drift. |
| Technology Stack & Persistence | PASS | Same stack throughout; no deviation. |
| Delivery & Documentation Requirements | PASS | No README change required — the multitenancy/authorization architecture description already documents the concept (System Admin / Tenant Admin standing); this migration doesn't change what's true, only how it's enforced internally. |

No violations requiring justification — Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/004-spring-security-admin-roles/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── README.md        # No contract changes — points back to 003's openapi.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/tbm/
│   ├── config/
│   │   └── SecurityConfig.java              # + @EnableMethodSecurity; JwtAuthenticationFilter/TenantContextFilter
│   │                                        #   wiring updated for their new constructor signatures
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java     # + AppUserRepository dep; grants ROLE_SYSTEM_ADMIN when isSystemAdmin
│   │   ├── TenantContextFilter.java         # bypass check reads ROLE_SYSTEM_ADMIN authority instead of its own lookup
│   │   └── TenantAuthorization.java         # NEW — @Component, isTenantAdmin(UUID tenantId) for @PreAuthorize SpEL
│   ├── tenant/
│   │   ├── TenantService.java               # requireSystemAdmin*/requireSystemAdminOrTenantAdmin removed;
│   │   │                                    #   @PreAuthorize added to list/create/get/update/delete; callerId param dropped
│   │   ├── TenantController.java            # principal param dropped from every method (no longer needed)
│   │   ├── MembershipService.java           # requireStandingFor removed; @PreAuthorize added to all 5 methods;
│   │   │                                    #   callerId param dropped
│   │   └── MembershipController.java        # principal param dropped from every method (no longer needed)
│   ├── user/
│   │   ├── AppUserService.java              # requireSystemAdmin removed; @PreAuthorize added to grant/revokeSystemAdmin;
│   │   │                                    #   callerId param dropped (last-admin invariant logic untouched)
│   │   └── UserAdminController.java         # principal param dropped from every method (no longer needed)
│   └── common/
│       ├── ApiExceptionHandler.java         # + handleAccessDenied(AccessDeniedException) → same 403 shape
│       │                                    #   ForbiddenException used to produce (see research.md §4)
│       └── exception/
│           └── ForbiddenException.java      # REMOVED — no remaining callers after migration
└── src/test/java/com/tbm/
    ├── integration/  # TenantCrudTest, SystemAdminGrantRevokeTest, SystemAdminConcurrentRevokeTest,
    │                 # TenantAdminGrantRevokeTest, TenantUpdateAuthorizationTest, NormalUserRoleBaselineTest,
    │                 # SystemAdminBeneficiarioAccessTest, MembershipManagementTest, TenantMembershipEnforcementTest
    │                 # — all drive the app via MockMvc/HTTP, so none needed source changes; verified unmodified
    │                 #   and green by a clean `mvn test` (T013)
    ├── security/
    │   └── TenantContextFilterTest.java            # constructor call updated to TenantContextFilter's new 2-arg
    │                                                #   signature (mock(AppUserRepository.class) argument dropped)
    └── unit/
        └── ApiExceptionHandlerTest.java             # unchanged — it only ever tested handleUnexpected, never had
                                                       #   a ForbiddenException case

frontend/   # untouched — no changes in scope for this feature
```

**Structure Decision**: Same `backend/`/`frontend/` layout as `001`–`003`; no new top-level
directories. All changes live in the existing `config`, `security`, `tenant`, and `user` packages
where the code being replaced already lives, plus one new class (`TenantAuthorization`) in
`security`, matching where `JwtAuthenticationFilter`/`TenantContextFilter` already live. No
front-end directory changes.

## Post-Design Constitution Check

*Re-evaluated after Phase 1 (data-model.md, contracts/README.md, quickstart.md).*

All eight rows of the Constitution Check table above still PASS with no changes: the Protected
Operations table in `data-model.md` confirms every one of the 13 currently-enforced checks (12
service methods + 1 filter bypass) maps to an equivalent declarative rule with no standing gaps;
`research.md §4`'s `ApiExceptionHandler.handleAccessDenied` design keeps Principle II's RFC 7807
shape intact (research.md §4 also records why the filter-level `AccessDeniedHandler` approach
tried first had to be replaced during implementation — it never actually ran);
`contracts/README.md` confirms zero contract drift (Principle III); no new dependency, schema
change, or manual setup step was introduced during design (Principles IV/V). No new complexity or
deviation was introduced during design.

## Complexity Tracking

*No violations — table not needed.*
