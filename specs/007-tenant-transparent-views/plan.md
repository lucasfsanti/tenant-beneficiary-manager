# Implementation Plan: Transparent Tenant Scoping via Database Views

**Branch**: `007-tenant-transparent-views` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-tenant-transparent-views/spec.md`

## Summary

Beneficiário's tenant-scoping keeps its existing request-side mechanism (the `X-Tenant-Id`
header, validated against the JWT's tenant memberships) unchanged, but moves the actual
filtering from application-code `WHERE tenant_id = ?` clauses into the database itself: the
`beneficiario` base table keeps its existing name, a `vw_beneficiario` **view** is created over
it (following the project's `vw_` naming convention for views) that filters by a Postgres session
variable (`app.tenant_id`), and the `Beneficiario` JPA entity is remapped to that view with its
`tenantId` column dropped entirely. Every transaction that touches Beneficiário data sets
`app.tenant_id` (via `set_config(..., true)`, transaction-scoped) before any query runs; if that
fails, the transaction aborts rather than running unfiltered. System Admin's existing cross-tenant
bypass is preserved and now also writes an audit record. The `vw_`/base-table naming is an
internal persistence-mapping detail — the public REST contract (`/api/beneficiarios/**`) is
unaffected (clarification session, 2026-08-20).

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.3.4

**Primary Dependencies**: Spring Web, Spring Data JPA (Hibernate 6.x, `ddl-auto: validate`),
Spring Security, Liquibase (`liquibase-core`), PostgreSQL JDBC driver, springdoc-openapi
2.6.0, jjwt (JWT issuance/parsing)

**Storage**: PostgreSQL 16, schema managed exclusively via append-only Liquibase changelogs
(`backend/src/main/resources/db/changelog/`)

**Testing**: JUnit 5 + Spring Boot Test (`@SpringBootTest`, `TestRestTemplate`), Testcontainers
(singleton `PostgreSQLContainer` shared across the integration test suite, see
`AbstractIntegrationTest`)

**Target Platform**: Linux server, containerized via `docker-compose` (backend + PostgreSQL +
frontend)

**Project Type**: Web application (`backend/` Spring Boot API + `frontend/` Vue 3 SPA); this
feature is backend-only — no frontend code changes are required (verified: no frontend code
reads a `tenantId` field off a Beneficiário API response)

**Performance Goals**: None beyond current behavior; no new performance target introduced by
this feature (per spec Assumptions)

**Constraints**: PostgreSQL-only (per constitution, no new database technology); the
active-tenant selector mechanism (`X-Tenant-Id` header + JWT membership validation) MUST NOT
change (FR-001); existing Beneficiário data MUST be preserved through migration (FR-010);
schema changes MUST be new, appended Liquibase changesets, never edits to existing ones (spec
002 convention)

**Scale/Scope**: One tenant-scoped entity today (Beneficiário); one new audit-log table; five
service methods (list/get/create/update/delete) plus one existing cross-tenant admin check
(`TenantService.delete`) need to route through the new session-context mechanism

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Multitenant Data Isolation (NON-NEGOTIABLE)** — PASS. This feature exists to strengthen
  this principle: filtering moves from a single centralized *application* layer to a single
  centralized *database* layer (the view + session variable), removing the possibility that a
  future repository method forgets a tenant predicate. Pessoa remains explicitly unfiltered and
  untouched (FR-009).
- **II. Data Integrity & Explicit Validation** — PASS. Existing business rules (matrícula
  uniqueness per tenant, Pessoa existence check) are unaffected — enforced by the same base-table
  constraints and service-level checks, just re-scoped through the view. Tenant-isolation
  rejections continue to return RFC 7807 `ProblemDetail` (FR-011), matching
  `com.tbm.common.ApiExceptionHandler`'s existing pattern.
- **III. API Contract Documentation** — PASS, actionable. FR-012 requires the OpenAPI-visible
  contract to drop `tenantId` from `BeneficiarioResponse`; springdoc generates this
  automatically from the DTO once the field is removed, no hand-maintained doc to update.
- **IV. Reproducible Zero-Touch Environment** — PASS. The view creation + audit-log table are
  one new, appended Liquibase changeset (`006-...sql`), applied automatically at boot like every
  prior changeset — no manual migration step.
- **V. Simplicity & Justified Technology Choices** — PASS. Uses native PostgreSQL views and
  session-scoped `set_config`/`current_setting` — no new library, ORM plugin, or Postgres
  extension (e.g., no `pgcrypto`-style RLS policies, no multi-tenancy framework). The new
  `TenantSessionContext` helper is a single small bean, scoped only to today's one tenant-scoped
  entity — not a speculative generic multi-entity abstraction.

No violations to justify; Complexity Tracking table is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/007-tenant-transparent-views/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── beneficiario-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/tbm/
│   ├── beneficiario/
│   │   ├── Beneficiario.java              # @Table renamed to vw_beneficiario; tenantId field removed
│   │   ├── BeneficiarioRepository.java    # tenantId-parameterized methods removed/simplified
│   │   ├── BeneficiarioService.java       # calls TenantSessionContext before each transaction
│   │   └── dto/BeneficiarioResponse.java  # tenantId field removed
│   ├── security/
│   │   ├── TenantContext.java             # unchanged (thread-local, request-scoped resolution)
│   │   ├── TenantContextFilter.java       # unchanged resolution/validation + new audit-log write
│   │   ├── TenantSessionContext.java      # NEW: applies resolved tenant to the DB session
│   │   └── TenantAccessAuditLog.java      # NEW entity (+ repository) for FR-013
│   └── tenant/
│       └── TenantService.java             # delete() re-pointed at the view via TenantSessionContext
├── src/main/resources/db/changelog/
│   ├── db.changelog-master.yaml           # + one new include line
│   └── 006-tenant-view-and-audit-log.sql  # NEW changeset (append-only)
└── src/test/java/com/tbm/
    ├── integration/                       # existing Beneficiário/SystemAdmin tests updated
    └── security/TenantContextFilterTest.java  # extended for audit-log assertions

frontend/                                   # no changes required by this feature
```

**Structure Decision**: Existing web-application layout (`backend/` Spring Boot API,
`frontend/` Vue 3 SPA) is unchanged. All work is within `backend/`: one new Liquibase
changeset, one changed entity/repository/service/DTO in the `beneficiario` package, two new
classes in the `security` package, and one call-site change in `tenant/TenantService.java`.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
