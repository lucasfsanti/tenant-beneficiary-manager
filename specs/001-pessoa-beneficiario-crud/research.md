# Phase 0 Research: Pessoa & Beneficiário Multitenant Registry

All Technical Context items were resolvable from the feature spec, the constitution, and the
explicit stack instructions given for this plan (`Java + Spring Boot, Spring Data, Spring
Security`, `Vue.js 3` with minimal visuals, `PostgreSQL`, one global database holding Pessoa plus
tenants and users). No open NEEDS CLARIFICATION items remain.

**Naming note** (per spec.md Clarifications, 2026-08-15, corrected): entity/field names already
established as Portuguese (Pessoa, Beneficiário, nome, cpf, matrícula, tipo, status, dataAdesao,
etc.) stay Portuguese, and all UI-facing text is Portuguese. "Tenant" is kept as "Tenant" — an
earlier draft of this document renamed it to "Cliente" and translated class/package suffixes
(Repository→Repositorio, Service→Servico, etc.); that rename has been retracted. Class, package,
and component names below use their original English naming.

## 1. Multitenancy isolation strategy

**Decision**: Shared database, shared schema, discriminator-column multitenancy. One PostgreSQL
database holds everything. Global tables (`pessoa`, `tenant`, `app_user`,
`user_tenant_membership`) carry no tenant column. The `beneficiario` table carries a `tenant_id`
foreign key. The active tenant for a request is resolved exactly once, from the authenticated
principal's session/token, by a single component (`TenantContext`, populated by a servlet
filter early in the security chain) and every Beneficiário repository call is routed through a
tenant-scoped base repository (a Spring Data `Specification`/`@Where`-style predicate, or a
`BeneficiarioRepository` wrapper that always injects `tenant_id = :activeTenantId`). Controllers
never accept a tenant id as a trusted client parameter for scoping reads/writes — the only
client-facing "tenant id" input is which membership to activate as the session's active tenant,
and that selection is itself validated against `user_tenant_membership` before being accepted.

**Rationale**: Matches the explicit instruction to use "one global database" with Pessoa/tenants/
users global, and satisfies Constitution Principle I's requirement for a single, centralized,
auditable enforcement point rather than scattering `tenant_id` checks through business logic.
Discriminator-column is also the simplest strategy to seed, migrate, and test at this project's
scale (Constitution Principle V — simplicity), compared to schema-per-tenant or database-per-
tenant, both of which would multiply migration/connection complexity for no isolation benefit at
this scale.

**Alternatives considered**:
- *Schema-per-tenant* (one Postgres schema per tenant, shared connection pool, `SET
  search_path`): stronger physical isolation, but adds schema-provisioning complexity and makes
  the explicitly requested "one global database with Pessoa/tenants/users" awkward, since Pessoa
  would need to live in yet another shared schema anyway. Rejected as unnecessary complexity for
  a reviewed CRUD exercise.
- *Database-per-tenant*: strongest isolation, but incompatible with "one global database" and
  with the requirement that Pessoa be trivially joinable/validatable from Beneficiário creation.
  Rejected.
- *Client-supplied tenant id trusted per-request* (e.g., tenant id in the URL path, unchecked):
  rejected outright — this is exactly the IDOR risk Principle I and spec FR-011/FR-021 rule out.

## 2. Tenant identification & simplified authentication

**Decision**: A lightweight login endpoint (`POST /api/auth/login`) accepts credentials for one
of the pre-seeded demo users and returns a signed JWT containing the user id and the list of
tenant ids the user is a member of (mirroring `user_tenant_membership`). The front-end stores the
token and a client-side "active tenant id" (defaulting to the first membership), sending both the
`Authorization: Bearer <token>` header and an `X-Tenant-Id` header on every Beneficiário request.
A Spring Security filter resolves the principal from the JWT, then validates that `X-Tenant-Id`
is one of the principal's memberships before populating `TenantContext`; if it is not, the
request is rejected (403) before reaching any repository — satisfying FR-021 and edge case
"user attempts to activate a tenant they are not associated with."

**Rationale**: Constitution Principle V explicitly allows simplified auth ("pre-seeded tenant
users") while still requiring it not be omitted. A stateless JWT keeps the backend simple (no
server-side session store needed for a Docker-Compose demo) and gives the front-end tenant
switcher a natural, tamper-evident source of truth (the token's membership claim) instead of a
trusted client value alone — the server re-validates on every request regardless.

**Alternatives considered**:
- *Server-side session (Spring Session)*: viable but adds a session store dependency
  (JDBC/Redis-backed) for no material benefit at this scale; rejected for simplicity.
- *No auth, tenant chosen freely via a plain dropdown with no user concept*: rejected after
  clarification — the spec now requires per-user tenant membership (User entity, FR-021), so
  "any user can pick any tenant" is explicitly out of scope.

## 3. CPF validation

**Decision**: A custom Jakarta Bean Validation annotation (`@Cpf`) implementing the standard
Brazilian CPF check-digit algorithm (11 digits, two check digits computed from weighted sums),
applied on the Pessoa DTO's `cpf` field, plus a `UNIQUE` database constraint on `pessoa.cpf` as
the authoritative guard against races.

**Rationale**: Required by spec FR-002/FR-003; a real check-digit algorithm (not just an
11-digit-length regex) is standard practice and avoids accepting obviously invalid CPFs.

**Alternatives considered**: Regex-only length/format check — rejected as insufficient per FR-002
("well-formed, check-digit-valid").

## 4. Standardized error handling

**Decision**: Spring 6's built-in `ProblemDetail` (RFC 7807) returned from a single
`@RestControllerAdvice`, mapping: Bean Validation failures → 400 with a `errors[]` extension
listing `{field, message}`; "Pessoa not found for Beneficiário creation" → 400 business-rule
violation; duplicate CPF / duplicate matrícula-in-tenant → 409 Conflict; cross-tenant record
access → 404 Not Found (never 403/leaking existence, per FR-011 and its edge case); unhandled
exceptions → 500 `ProblemDetail` (still structured, never a raw stack trace). Validation and
business-rule message text is written in Portuguese; the `ProblemDetail`
`type`/`title`/`detail`/`status`/`instance` field names themselves are unchanged, as RFC 7807
defines them.

**Rationale**: Directly satisfies Constitution Principle II and the constitution's explicit RFC
7807 delivery requirement; springdoc-openapi documents `ProblemDetail` responses automatically.

**Alternatives considered**: A custom hand-rolled error envelope — rejected, since RFC 7807 is
explicitly named in both the brief and the constitution, and Spring ships first-class support.

## 5. API documentation

**Decision**: `springdoc-openapi-starter-webmvc-ui`, generating both the OpenAPI document and a
Swagger UI page directly from controller/DTO annotations, exposed at boot with no extra step.

**Rationale**: Constitution Principle III requires docs generated from/verified against code, not
hand-maintained; springdoc is the standard Spring Boot 3.x choice.

**Alternatives considered**: Hand-written OpenAPI YAML maintained separately — rejected, drifts
from implementation and violates Principle III.

## 6. Migrations & zero-touch seed data

**Decision**: Flyway, versioned SQL migrations under `src/main/resources/db/migration/`,
executed automatically on Spring Boot startup (`spring.flyway.enabled=true`, default). Schema
migrations (`V1__schema.sql`) create all tables/constraints/indexes. A seed migration
(`V2__seed_demo_data.sql`) inserts ≥2 demo tenants, ≥2 demo users — at least one with membership
in 2+ tenants and at least one restricted to exactly one tenant, satisfying FR-017 and making the
FR-021/SC-009 denial path demonstrable from seed data alone — a handful of demo Pessoas, and demo
Beneficiário rows split across tenants — all with fixed, deterministic ids so the quickstart guide
can reference them by name.

**Rationale**: Constitution Principle IV requires migrations to be the schema source of truth and
seed data to run automatically with zero manual steps; Flyway migrations satisfy both by running
once, deterministically, on first container startup against an empty database volume.

**Alternatives considered**: Hibernate `ddl-auto=update` — explicitly prohibited by Principle IV.
A `CommandLineRunner` that seeds via JPA on every boot — rejected in favor of SQL seed migrations,
which are simpler to make idempotent (they run exactly once, tracked by Flyway's history table)
and keep all schema+data provenance in one versioned place.

## 7. Backend & frontend testing approach

**Decision**: Backend — JUnit 5 with Spring Boot Test slices for fast unit tests (validators,
services) and Testcontainers-backed `@SpringBootTest` integration/contract tests against a real
PostgreSQL container, including a dedicated cross-tenant isolation test suite that asserts a
user active in tenant A gets 404 (not data) for a tenant B Beneficiário id. Frontend — Vitest +
`@vue/test-utils` for component/store unit tests (tenant switcher restricted to memberships,
form validation error rendering). No end-to-end browser test suite is introduced, consistent with
keeping scope to what the spec and constitution require.

**Rationale**: Testcontainers gives realistic Postgres-specific behavior (unique constraints,
Flyway migrations) instead of an in-memory substitute; a dedicated isolation test suite directly
exercises Constitution Principle I, the non-negotiable guarantee.

**Alternatives considered**: H2 in-memory database for tests — rejected because the constitution
requires PostgreSQL as the persistence target and an H2 substitute risks masking Postgres-specific
constraint/migration behavior.

## 8. Frontend minimalism

**Decision**: Plain Vue 3 (Composition API) + Vite, no component/UI kit (no Vuetify, Element
Plus, etc.) — a small set of hand-written, unstyled-by-default components with minimal shared CSS
(a single stylesheet, system font stack, no design system). Pinia for the auth/tenant/pessoa/
beneficiario stores; Vue Router for the four views + login; Axios with a request interceptor that
attaches the JWT and the active-tenant header. All UI-facing labels, button text, and validation
messages are in Portuguese (per spec.md FR-025); component/file names stay in English, consistent
with the corrected clarification limiting the Portuguese-naming requirement to entities/fields
and UI text.

**Rationale**: Directly follows the explicit "front-end... visuals must be minimal" instruction
and Constitution Principle V (no speculative dependencies beyond what the CRUD/tenant-switch
scope requires).

**Alternatives considered**: A component library (faster visual polish) — rejected as contrary to
the explicit minimal-visuals instruction and as an unjustified added dependency under Principle V.

## 9. Pagination defaults

**Decision**: Spring Data `Pageable`, default page size 20 (configurable via query params `page`,
`size`, capped at e.g. 100), default sort by the linked Pessoa's name ascending, exposed through
`GET /api/beneficiarios?pessoaNome=&status=&page=&size=`.

**Rationale**: Satisfies FR-013/FR-014/SC-005 with an industry-standard, unsurprising default;
matches the Assumptions section of the spec, which left exact defaults to design.

**Alternatives considered**: Cursor-based pagination — more complex to implement/test and not
warranted at this scale; rejected under the simplicity principle.
