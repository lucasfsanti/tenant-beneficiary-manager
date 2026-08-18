<!--
Sync Impact Report
==================
Version change: N/A (unratified template) → 1.0.0
Rationale: Initial ratification. No prior version existed — the file only contained
unfilled [PLACEHOLDER] tokens — so this is a MAJOR (first-adoption) version, not an
amendment.

Modified principles: none (initial creation)
Added sections:
  - Core Principles I–V (Multitenant Data Isolation, Data Integrity & Explicit
    Validation, API Contract Documentation, Reproducible Zero-Touch Environment,
    Simplicity & Justified Technology Choices)
  - Technology Stack & Persistence
  - Delivery & Documentation Requirements
  - Governance
Removed sections: none

Deferred / TODO placeholders: none — all template tokens were resolved from the
project brief supplied by the user.

Follow-up: none required for this command. Non-governance intents extracted from
the user's input are listed under "Next Actions" below.
-->

# Tenant Beneficiary Manager Constitution

## Core Principles

### I. Multitenant Data Isolation (NON-NEGOTIABLE)
Beneficiário records MUST be strictly scoped to the tenant that owns them. Every
query, mutation, and listing endpoint for Beneficiário MUST filter by the tenant
resolved from the request context; a tenant MUST NEVER be able to read, edit,
delete, or enumerate another tenant's Beneficiário records — including by guessing
or brute-forcing another tenant's record identifier (no IDOR). Pessoa is a global
entity, shared across all tenants, and MUST NOT be filtered or restricted by
tenant. The tenant identification mechanism (e.g., request header, JWT claim, or
path segment) MUST be resolved and enforced at a single, centralized, auditable
layer (e.g., a Spring Security filter, an interceptor, or a repository-level
default clause) — never re-implemented ad hoc inside individual controllers or
services.

Rationale: Cross-tenant data leakage is the single greatest risk this system is
built to prevent. Centralizing enforcement makes isolation reviewable and testable
as one concern, instead of a property that must be manually re-verified on every
new endpoint.

### II. Data Integrity & Explicit Validation
Business rules — CPF validity, required fields, matrícula uniqueness per tenant,
and existence of the referenced Pessoa before creating a Beneficiário — MUST be
enforced server-side before persistence, regardless of any client-side checks in
the Vue front-end, since the API is the actual trust boundary. Validation failures
and business-rule violations MUST return structured, actionable error responses
(RFC 7807 Problem Details) with a clear machine-readable type/code and a
human-readable message. A bare HTTP 500 or an unhandled stack trace in response to
a foreseeable input error is treated as a defect, not an acceptable failure mode.

Rationale: Explicit, typed errors let the front-end surface useful feedback to the
user and are an explicit requirement of the project brief; silent failures or
generic 500s make the system unusable and undebuggable.

### III. API Contract Documentation
Every REST endpoint MUST be described through OpenAPI/Swagger, generated from or
verified against the actual implementation (annotations/code-first), never
hand-maintained as a separate, driftable document. The generated documentation
MUST be reachable without extra setup once the stack is running (e.g., a Swagger
UI route). Changes that alter a contract (request/response shape, status codes,
tenant-scoping semantics) MUST be reflected in the documentation in the same
change.

Rationale: Accurate, live API documentation is how reviewers, front-end
developers, and future maintainers understand tenant and auth semantics without
reading every controller.

### IV. Reproducible, Zero-Touch Environment
The full stack (PostgreSQL, backend, front-end) MUST start with a single
`docker-compose up` (or equivalent) and require no manual step afterward —
including schema migrations and any seed data (e.g., pre-registered demo
tenants), which MUST run automatically on startup. Database schema changes MUST
be expressed as versioned migrations (Flyway or Liquibase applied at boot);
hand-edited schemas or reliance on ORM auto-DDL (e.g., Hibernate
`ddl-auto=update`) as the source of truth is prohibited.

Rationale: A reviewer or teammate must be able to clone the repo and get a
working, correctly isolated, correctly seeded system with one command — this is
an explicit delivery requirement and the only reliable way to verify the
isolation and validation guarantees hold from a clean state.

### V. Simplicity & Justified Technology Choices
Default to the requested stack: Java/Spring Boot with Spring Data and Spring
Security on the backend, Vue.js 3 on the front-end, PostgreSQL for persistence.
Any deviation from this stack (different framework, database, or added
infrastructure) MUST be documented in the README with an explicit rationale.
Authentication MAY be simplified (e.g., a small set of pre-seeded tenant
users/logins) but MUST NOT be omitted, and MUST NOT be over-built beyond what is
needed to identify the acting user and tenant. Do not introduce speculative
abstractions (generic multi-database drivers, plugin architectures, unused
configuration knobs) that the stated scope does not call for.

Rationale: The brief explicitly asks for justification of any stack deviation and
for "some simplified" authentication layer rather than a production-grade IAM
system — matching effort to actual scope keeps the system reviewable and keeps
the isolation and validation principles from being obscured by incidental
complexity.

## Technology Stack & Persistence

- Backend: Java, Spring Boot, Spring Data (JPA), Spring Security.
- Front-end: Vue.js 3.
- Persistence: PostgreSQL, run via Docker; schema managed exclusively through
  versioned migrations (Flyway or Liquibase).
- Multitenancy identification mechanism (e.g., tenant header, JWT claim, or
  session/login-selected tenant) is an architectural decision that MUST be made
  explicit and documented in the README, including why it was chosen over
  alternatives (e.g., separate schema-per-tenant vs. discriminator column).
- Containerization: the entire system (database, backend, front-end) MUST be
  defined in `docker-compose.yml` (or equivalent) and MUST come up healthy with a
  single command.

## Delivery & Documentation Requirements

- The README MUST document: how to run the project end-to-end, the architectural
  decisions taken (especially the multitenancy isolation strategy) and why they
  were chosen, and what would be done differently given more time.
- API documentation MUST be exposed via OpenAPI/Swagger and MUST stay accurate
  relative to the running code (see Principle III).
- Error responses MUST follow a standardized shape (RFC 7807 Problem Details) as
  described in Principle II — this applies to validation errors, not-found
  errors, and tenant-isolation violations alike.
- The GitHub repository MUST contain the complete source for both backend and
  front-end; nothing required to build or run the system may live outside the
  repo.

## Governance

This constitution supersedes ad hoc practice for this project. Any pull request
or code review MUST verify compliance with the Core Principles above, especially
Principle I (Multitenant Data Isolation) for any change touching Beneficiário
read/write paths.

Amendment procedure:
1. Propose the change (what principle/section, and why) in the PR or issue
   description.
2. Update this file in the same change, including the Sync Impact Report at the
   top of the file.
3. Bump `CONSTITUTION_VERSION` following semantic versioning: MAJOR for backward
   incompatible principle removals/redefinitions, MINOR for new principles or
   materially expanded guidance, PATCH for clarifications and wording fixes.
4. Update `Last Amended` to the date of the change.

Complexity or deviation from a stated principle (e.g., a stack change under
Principle V, or a relaxation of Principle IV's zero-touch requirement) MUST be
justified in writing (README or PR description) rather than silently introduced.

**Version**: 1.0.0 | **Ratified**: 2026-08-15 | **Last Amended**: 2026-08-15
