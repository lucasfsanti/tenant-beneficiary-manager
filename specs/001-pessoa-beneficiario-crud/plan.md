# Implementation Plan: Pessoa & Beneficiário Multitenant Registry

**Branch**: `001-pessoa-beneficiario-crud` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-pessoa-beneficiario-crud/spec.md`

## Summary

A full-stack registry with two data shapes: a global Pessoa registry (nome + cpf required,
dataNascimento + email optional, shared across the platform) and a per-tenant Beneficiário
registry that links a Pessoa to one tenant with tenant-specific attributes (matrícula, tipo —
TITULAR/DEPENDENTE, status — ATIVO/INATIVO, dataAdesao). Isolation is enforced by a single shared
PostgreSQL database holding global tables (Pessoa, Tenant, User, User↔Tenant membership) plus a
`tenant_id`-discriminated Beneficiário table, with tenant scoping resolved once per request from
the authenticated user's session/token and enforced centrally at the repository layer — never
trusted from client-supplied input beyond selecting among the user's own memberships. Backend is
Java/Spring Boot (Spring Data JPA, Spring Security), front-end is a minimal Vue.js 3 SPA with a
tenant switcher limited to the signed-in user's memberships, and the whole stack (Postgres,
backend, frontend) starts via a single `docker-compose up` with Flyway migrations and demo seed
data applied automatically. Entity/field names already established as Portuguese (Pessoa,
Beneficiário, nome, cpf, matrícula, tipo, status, dataAdesao, etc.) stay Portuguese, and all UI
text is Portuguese; "Tenant" itself and general code identifiers are not required to be
translated (spec.md Clarifications, 2026-08-15, corrected).

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3.x) · JavaScript (ES2022) for the Vue 3 front-end, no TypeScript (kept minimal per Constitution Principle V)

**Primary Dependencies**: Spring Boot Web, Spring Data JPA, Spring Security, springdoc-openapi (Swagger UI/OpenAPI generation), Flyway (migrations), Jakarta Bean Validation; Vue 3 (Composition API) + Vite, Vue Router, Pinia, Axios

**Storage**: PostgreSQL 16, single shared database/schema — global tables (`pessoa`, `tenant`, `app_user`, `user_tenant_membership`) plus a tenant-discriminated `beneficiario` table (`tenant_id` column, enforced isolation)

**Testing**: JUnit 5 + Spring Boot Test (unit, `@WebMvcTest`/`@DataJpaTest` slices) + Testcontainers (PostgreSQL) for integration/contract tests, incl. dedicated cross-tenant isolation tests; Vitest + @vue/test-utils for front-end unit/component tests

**Target Platform**: Linux containers via Docker Compose (Postgres + Spring Boot backend + Nginx-served Vue build), accessed through a desktop web browser

**Project Type**: Web application (backend + frontend, two-project structure)

**Performance Goals**: No strict SLA specified by the feature; standard responsive-web behavior is sufficient at the demo/review scale described in the spec (single-digit tenants, tens–hundreds of records per tenant, SC-005's 50-record pagination scenario)

**Constraints**: Full stack MUST start with `docker-compose up` and zero manual steps (migrations + seed data run automatically, per Constitution Principle IV); all error responses MUST be RFC 7807 Problem Details (Principle II); every endpoint MUST be documented via OpenAPI/Swagger (Principle III); cross-tenant Beneficiário access MUST be impossible even via direct identifier guessing (Principle I); entity/field names (already Portuguese) and all UI text MUST be in Brazilian Portuguese; "Tenant" and general code identifiers are not required to be translated (spec.md Clarifications, 2026-08-15, corrected)

**Scale/Scope**: 2 CRUD resource types (Pessoa, Beneficiário) plus supporting Tenant/User/membership data; ≥2 pre-configured tenants and ≥1 pre-configured user with multi-tenant membership shipped as seed data; 4 front-end views (Pessoa list/form, Beneficiário list/form) plus login and a tenant switcher

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. Multitenant Data Isolation (NON-NEGOTIABLE) | PASS | Shared DB, `tenant_id` discriminator on `beneficiario`; active tenant resolved once from the authenticated principal and enforced in a single centralized layer (a tenant-scoping base repository/specification used by every Beneficiário query — see research.md), never from a client-controlled path parameter alone. Pessoa/Tenant/User remain unfiltered globals. |
| II. Data Integrity & Explicit Validation | PASS | Jakarta Bean Validation + a custom CPF check-digit validator; a `@RestControllerAdvice` maps validation and business-rule exceptions to RFC 7807 `ProblemDetail` responses with field-level detail (message text in Portuguese) — see research.md. |
| III. API Contract Documentation | PASS | springdoc-openapi generates OpenAPI/Swagger UI directly from controller code, so docs cannot drift from the implementation; design-time contract captured in `contracts/openapi.yaml`. |
| IV. Reproducible, Zero-Touch Environment | PASS | `docker-compose.yml` defines db/backend/frontend; Flyway versioned migrations run on backend boot, including a seed migration for demo tenants/users/pessoas/beneficiarios — no manual step required. |
| V. Simplicity & Justified Technology Choices | PASS | Uses exactly the requested stack (Spring Boot/Spring Data/Spring Security, Vue 3, PostgreSQL); front-end kept to plain JS + minimal hand-written CSS (no UI kit) per explicit "visuals must be minimal" instruction; auth is a simplified login (pre-seeded users, no external IdP) — see research.md for the concrete mechanism. |
| Technology Stack & Persistence | PASS | Matches exactly: Java/Spring Boot/Spring Data/Spring Security, Vue 3, PostgreSQL via Docker, Flyway-managed schema. Multitenancy mechanism (shared DB/schema, discriminator column, membership-checked active-tenant header) documented here and in research.md for the README. |
| Delivery & Documentation Requirements | PASS (tracked) | README, OpenAPI/Swagger, RFC 7807 errors, and full source for both apps are required deliverables of the implementation phase, not of this plan — flagged here so `/speckit-tasks` includes them. |

No violations requiring justification — Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-pessoa-beneficiario-crud/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── openapi.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/tbm/
│   ├── config/                 # SecurityConfig, OpenApiConfig, JPA/Flyway config
│   ├── security/                # TenantContext, JWT filter, membership-checked tenant resolver
│   ├── common/                  # ProblemDetail advice, CPF validator, pagination helpers
│   ├── pessoa/                  # entity, repository, service, controller, dto
│   ├── tenant/                  # entity, repository, dto
│   ├── user/                    # entity, repository, auth (login), membership
│   └── beneficiario/            # entity, repository (tenant-scoped), service, controller, dto
├── src/main/resources/
│   ├── db/migration/            # Flyway: schema + seed (V1__schema.sql, V2__seed_demo_data.sql, ...)
│   └── application.yml
└── src/test/java/com/tbm/
    ├── unit/                    # validators, mappers, service-layer rules
    ├── integration/             # @DataJpaTest / @SpringBootTest + Testcontainers (incl. isolation tests)
    └── contract/                # controller-level contract tests against contracts/openapi.yaml

frontend/
├── src/
│   ├── components/              # shared UI (TenantSwitcher, PaginationControl, ErrorBanner, ...) — all UI text in Portuguese
│   ├── views/                   # LoginView, PessoaListView, PessoaFormView, BeneficiarioListView, BeneficiarioFormView — all UI text in Portuguese
│   ├── stores/                  # Pinia: auth (user + memberships + active tenant), pessoa, beneficiario
│   ├── services/                # api.js (Axios instance + interceptors), pessoaApi.js, beneficiarioApi.js
│   ├── router/
│   ├── App.vue
│   └── main.js
└── tests/
    └── unit/                    # Vitest + @vue/test-utils

docker-compose.yml                # db (postgres) + backend + frontend, single `docker-compose up`
```

**Structure Decision**: Two-project web application layout (`backend/` Spring Boot Maven/Gradle
project, `frontend/` Vue 3 Vite project), orchestrated by a root `docker-compose.yml`, matching
Constitution's "Technology Stack & Persistence" section and the explicit backend/frontend split
requested in the feature brief.

## Post-Design Constitution Check

*Re-evaluated after Phase 1 (data-model.md, contracts/openapi.yaml, quickstart.md).*

All seven rows of the Constitution Check table above still PASS with no changes: the shared-
database/discriminator-column design (data-model.md), the centralized tenant-scoping enforcement
point (research.md §1, contract's `X-Tenant-Id` parameter + 403/404 semantics), the RFC 7807
`ProblemDetail` schema used throughout `contracts/openapi.yaml`, the Flyway schema+seed
migrations (research.md §6), and the Brazilian Portuguese naming for entities/fields and UI text
(spec.md Clarifications, 2026-08-15, corrected) are all now concretely specified, not just
asserted. No new complexity or deviation was introduced during design.

## Complexity Tracking

> No entries — Constitution Check has no unresolved violations to justify.
