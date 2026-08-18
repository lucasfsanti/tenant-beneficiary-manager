---

description: "Task list for Pessoa & Beneficiário Multitenant Registry"
---

# Tasks: Pessoa & Beneficiário Multitenant Registry

**Input**: Design documents from `/specs/001-pessoa-beneficiario-crud/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md (all present)

**Tests**: Included. research.md §7 and §1 commit to Testcontainers-backed integration tests, including a dedicated cross-tenant isolation suite that directly exercises Constitution Principle I (NON-NEGOTIABLE) — these are treated as required deliverables, not optional extras.

**Organization**: Tasks are grouped by user story (spec.md priorities P1–P4) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)
- File paths are relative to the repository root

## Path Conventions

Web app per plan.md: `backend/src/main/java/com/tbm/...` (Spring Boot), `backend/src/test/java/com/tbm/...`, `frontend/src/...` (Vue 3), `frontend/tests/...`.

## Naming Conventions (per spec.md Clarifications, 2026-08-15, corrected)

- Entity/field names already established as Portuguese stay Portuguese: `Pessoa` (nome, cpf, dataNascimento, email), `Beneficiario` (pessoaId, tenantId, matricula, tipo, status, dataAdesao).
- **"Tenant" is kept as "Tenant"** — it is NOT translated (class names, `tenant_id`, `X-Tenant-Id`, package `com.tbm.tenant`, etc. all stay as originally named).
- General Java/Vue code identifiers (class/package/component names) are not required to be translated.
- **All UI-facing text (labels, buttons, messages, validation errors) MUST be in Brazilian Portuguese** (FR-025, SC-008) — flagged explicitly on every frontend task below.
- FR-005's Pessoa-deletion-block message MUST be generic and MUST NOT name the referencing tenant(s) (2026-08-15 clarification) — flagged on T028/T032 below.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 [P] Create Spring Boot 3.3.x / Java 21 backend project skeleton in `backend/` (Maven, with Spring Web, Spring Data JPA, Spring Security, Jakarta Validation, Flyway, PostgreSQL driver, springdoc-openapi, JUnit 5, Spring Boot Test, Testcontainers dependencies) per plan.md Project Structure
- [X] T002 [P] Create Vue 3 + Vite frontend project skeleton in `frontend/` (with vue-router, pinia, axios, vitest, @vue/test-utils dependencies) per plan.md Project Structure
- [X] T003 [P] Configure backend formatting/linting and base `backend/src/main/resources/application.yml` (Postgres datasource, Flyway enabled, server port)
- [X] T004 [P] Configure frontend ESLint/Prettier and base `frontend/vite.config.js`
- [X] T005 Create root `docker-compose.yml` wiring `db` (postgres:16), `backend`, and `frontend` services with healthchecks and `depends_on` ordering (db → backend → frontend)
- [X] T006 [P] Create `backend/Dockerfile` (multi-stage Maven build → JRE runtime)
- [X] T007 [P] Create `frontend/Dockerfile` (Vite build → static/Nginx serve)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented — every story requires an authenticated, tenant-resolved, error-standardized, documented API surface.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T008 Create Flyway schema migration `backend/src/main/resources/db/migration/V1__schema.sql` with `tenant`, `app_user`, `user_tenant_membership`, `pessoa`, and `beneficiario` tables, all constraints/FKs/unique indexes per data-model.md
- [X] T009 Create Flyway seed migration `backend/src/main/resources/db/migration/V2__seed_demo_data.sql` with ≥2 tenants, ≥2 users (at least one with membership in 2+ tenants, and at least one restricted to exactly one tenant, per FR-017), memberships, demo Pessoas, and demo Beneficiários split across tenants, per research.md §6 (depends on T008)
- [X] T010 [P] Implement `Tenant` JPA entity + repository in `backend/src/main/java/com/tbm/tenant/`
- [X] T011 [P] Implement `AppUser` JPA entity (`id`, `username`, `password_hash`, `created_at` — no `display_name` field, per data-model.md) + repository in `backend/src/main/java/com/tbm/user/`
- [X] T012 [P] Implement `UserTenantMembership` JPA entity + repository in `backend/src/main/java/com/tbm/user/`
- [X] T013 Implement JWT issuing/parsing utility in `backend/src/main/java/com/tbm/security/JwtService.java`
- [X] T014 Implement Spring Security config (stateless sessions, JWT auth filter, permit `/api/auth/login` and Swagger paths, authenticate everything else) in `backend/src/main/java/com/tbm/config/SecurityConfig.java` (depends on T013)
- [X] T015 Implement `TenantContext` + tenant-header validation filter (resolves active tenant from `X-Tenant-Id`, rejects with 403 if not one of the caller's memberships, before any repository access) in `backend/src/main/java/com/tbm/security/TenantContextFilter.java` (depends on T012, T014)
- [X] T016 Implement `AuthController` with `POST /api/auth/login` and `GET /api/me` (returns profile — `id`/`username`/`tenants`, no display name — + tenant memberships) in `backend/src/main/java/com/tbm/user/AuthController.java` (depends on T011, T012, T013)
- [X] T017 Implement global RFC 7807 error handling via `@RestControllerAdvice` (validation errors, business-rule conflicts, not-found, unhandled → `ProblemDetail`; all `title`/`detail` message text in Portuguese) in `backend/src/main/java/com/tbm/common/ApiExceptionHandler.java`
- [X] T018 [P] Configure springdoc-openapi (bearer security scheme, info block) in `backend/src/main/java/com/tbm/config/OpenApiConfig.java`
- [X] T019 [P] Implement CPF check-digit validator (`@Cpf` annotation + `ConstraintValidator`) in `backend/src/main/java/com/tbm/common/validation/CpfValidator.java`
- [X] T020 Implement shared Testcontainers PostgreSQL base test class in `backend/src/test/java/com/tbm/integration/AbstractIntegrationTest.java` (depends on T008)
- [X] T021 [P] Implement Axios API client with JWT + `X-Tenant-Id` request interceptors in `frontend/src/services/api.js`
- [X] T022 [P] Implement Pinia auth store (user, memberships, active tenant, login/logout actions) in `frontend/src/stores/auth.js` (depends on T021)
- [X] T023 [P] Implement Vue Router with routes for login, Pessoas, Beneficiários, plus an auth guard, in `frontend/src/router/index.js` (depends on T022)
- [X] T024 [P] Implement `LoginView` (all labels/buttons/messages in Portuguese, per FR-025) in `frontend/src/views/LoginView.vue` (depends on T021, T022)
- [X] T025 [P] Implement shared `ErrorBanner` component rendering RFC 7807 problem details (title/detail/field errors — text already Portuguese from the backend, per T017) in `frontend/src/components/ErrorBanner.vue`

**Checkpoint**: Foundation ready — auth, tenant resolution, error format, and API docs scaffolding all work; user story implementation can now begin.

---

## Phase 3: User Story 1 - Maintain the Global Pessoa Registry (Priority: P1) 🎯 MVP

**Goal**: Authorized users can create, list, view, edit, and delete Pessoa records in a single global registry, independent of tenant, with CPF validation and uniqueness enforced.

**Independent Test**: Create, list, edit, and delete Pessoa records with no tenant context involved; confirm CPF validation/uniqueness and delete-blocking work.

### Tests for User Story 1 ⚠️

> Write these tests FIRST, ensure they FAIL before implementation

- [X] T026 [P] [US1] Unit test for `@Cpf` validator (valid CPFs pass, bad check-digit/format rejected) in `backend/src/test/java/com/tbm/unit/CpfValidatorTest.java`
- [X] T027 [P] [US1] Integration test: Pessoa create/list/get/update, duplicate-CPF → 409, invalid-CPF → 400, missing-nome → 400, optional dataNascimento/email accepted when present or omitted in `backend/src/test/java/com/tbm/integration/PessoaIntegrationTest.java` (depends on T020)
- [X] T028 [P] [US1] Integration test: deleting a Pessoa referenced by a Beneficiário row is blocked with a 409 Problem Detail whose message is generic and does not name the referencing tenant(s) in `backend/src/test/java/com/tbm/integration/PessoaDeletionRestrictionTest.java` (depends on T020)

### Implementation for User Story 1

- [X] T029 [P] [US1] Implement `Pessoa` JPA entity (`nome` required, `cpf` required/unique, `dataNascimento` optional, `email` optional) in `backend/src/main/java/com/tbm/pessoa/Pessoa.java`
- [X] T030 [US1] Implement `PessoaRepository` in `backend/src/main/java/com/tbm/pessoa/PessoaRepository.java` (depends on T029)
- [X] T031 [US1] Implement Pessoa DTOs (`PessoaInput` with `nome`, `cpf`, optional `dataNascimento`/`email`; `PessoaResponse`; `PessoaPage`) in `backend/src/main/java/com/tbm/pessoa/dto/` (depends on T029)
- [X] T032 [US1] Implement `PessoaService` (create/list/get/update/delete, CPF-uniqueness pre-check, delete-block pre-check against `beneficiario` using an existence-only query with a generic, tenant-agnostic error message — never naming which tenant(s) reference the Pessoa) in `backend/src/main/java/com/tbm/pessoa/PessoaService.java` (depends on T030, T031, T019)
- [X] T033 [US1] Implement `PessoaController` (`GET /api/pessoas` with optional `nome` filter + pagination, `POST`, `GET/{id}`, `PUT/{id}`, `DELETE/{id}`) in `backend/src/main/java/com/tbm/pessoa/PessoaController.java` (depends on T032, T017, T018)
- [X] T034 [P] [US1] Implement `pessoaApi.js` service in `frontend/src/services/pessoaApi.js` (depends on T021)
- [X] T035 [P] [US1] Implement Pinia Pessoa store in `frontend/src/stores/pessoa.js` (depends on T034)
- [X] T036 [US1] Implement `PessoaListView` (list + delete action, error display; all labels/buttons/messages in Portuguese, per FR-025) in `frontend/src/views/PessoaListView.vue` (depends on T035, T025, T023)
- [X] T037 [US1] Implement `PessoaFormView` (create/edit: nome, cpf, optional dataNascimento/email; CPF/nome validation error display; all labels/buttons/messages in Portuguese, per FR-025) in `frontend/src/views/PessoaFormView.vue` (depends on T035, T025, T023)

**Checkpoint**: User Story 1 is fully functional and independently testable/demoable.

---

## Phase 4: User Story 2 - Manage Beneficiários Within the Active Tenant (Priority: P2)

**Goal**: Authorized users can create, list, view, edit, and delete Beneficiário records scoped to the active tenant, linking to an existing Pessoa, with matrícula uniqueness enforced per tenant.

**Independent Test**: Given pre-existing Pessoa records and one active tenant, create a Beneficiário, then edit, view, and remove it — confirming tenant-specific fields and validations behave correctly.

### Tests for User Story 2 ⚠️

- [X] T038 [P] [US2] Integration test: Beneficiário creation succeeds when linked Pessoa exists; rejected (400) when it does not; omitting `dataAdesao` on creation defaults it to the record's creation date (FR-023) in `backend/src/test/java/com/tbm/integration/BeneficiarioCreationTest.java` (depends on T020)
- [X] T039 [P] [US2] Integration test: matrícula uniqueness enforced within a tenant (409 on duplicate) but allowed to repeat across different tenants in `backend/src/test/java/com/tbm/integration/BeneficiarioMatriculaUniquenessTest.java` (depends on T020)
- [X] T040 [P] [US2] Integration test: Beneficiário update and delete within the active tenant, confirming the linked Pessoa is untouched by delete in `backend/src/test/java/com/tbm/integration/BeneficiarioCrudTest.java` (depends on T020)
- [X] T040a [P] [US2] Integration test: Beneficiário creation rejected with 400 when `matricula`, `tipo`, or `status` is missing, and when `tipo`/`status` holds a value outside their enums (FR-008, FR-019) in `backend/src/test/java/com/tbm/integration/BeneficiarioValidationTest.java` (depends on T020)

### Implementation for User Story 2

- [X] T041 [P] [US2] Implement `Beneficiario` JPA entity (`tenant_id`, `pessoa_id`, `matricula`, `tipo` enum TITULAR/DEPENDENTE, `status` enum ATIVO/INATIVO, `data_adesao` optional — defaults to creation date if omitted, per FR-023) in `backend/src/main/java/com/tbm/beneficiario/Beneficiario.java`
- [X] T042 [US2] Implement tenant-scoped `BeneficiarioRepository` (every query pre-filtered by the resolved `TenantContext` tenant id) in `backend/src/main/java/com/tbm/beneficiario/BeneficiarioRepository.java` (depends on T041, T015)
- [X] T043 [US2] Implement Beneficiário DTOs (`BeneficiarioInput` with `pessoaId`, `matricula`, `tipo`, `status`, optional `dataAdesao` — no client-supplied `tenantId`, per FR-024; `BeneficiarioResponse`; `BeneficiarioPage`) in `backend/src/main/java/com/tbm/beneficiario/dto/` (depends on T041)
- [X] T044 [US2] Implement `BeneficiarioService` (create with Pessoa-exists + matrícula-uniqueness checks, get/update/delete — all tenant-scoped) in `backend/src/main/java/com/tbm/beneficiario/BeneficiarioService.java` (depends on T042, T043, T030)
- [X] T045 [US2] Implement `BeneficiarioController` (`POST`, `GET/{id}`, `PUT/{id}`, `DELETE/{id}`, basic `GET` list scoped to the active tenant) in `backend/src/main/java/com/tbm/beneficiario/BeneficiarioController.java` (depends on T044)
- [X] T046 [P] [US2] Implement `beneficiarioApi.js` service in `frontend/src/services/beneficiarioApi.js` (depends on T021)
- [X] T047 [P] [US2] Implement Pinia Beneficiário store in `frontend/src/stores/beneficiario.js` (depends on T046)
- [X] T048 [US2] Implement `BeneficiarioListView` (basic list scoped to active tenant, delete action; all labels/buttons/messages in Portuguese, per FR-025) in `frontend/src/views/BeneficiarioListView.vue` (depends on T047, T025, T023)
- [X] T049 [US2] Implement `BeneficiarioFormView` (create/edit: pick Pessoa, matrícula, tipo, status, optional dataAdesao; all labels/buttons/messages in Portuguese, per FR-025) in `frontend/src/views/BeneficiarioFormView.vue` (depends on T047, T025, T035)

**Checkpoint**: User Stories 1 and 2 both work independently — the core CRUD value of the platform is complete.

---

## Phase 5: User Story 3 - Switch Active Tenant and Confirm Isolation (Priority: P3)

**Goal**: Users can switch the active tenant among their own memberships via a visible control, with all Beneficiário views immediately re-scoped and cross-tenant access impossible.

**Independent Test**: With Beneficiário records seeded in ≥2 tenants, switch the active tenant in the UI and confirm listing/search/direct-access views only ever show the newly active tenant's records.

### Tests for User Story 3 ⚠️

- [X] T050 [P] [US3] Integration test: `GET /api/beneficiarios/{id}` for a record belonging to another tenant returns 404 with no indication it exists elsewhere in `backend/src/test/java/com/tbm/integration/TenantIsolationTest.java` (depends on T045)
- [X] T051 [P] [US3] Integration test: `X-Tenant-Id` set to a tenant the caller is not a member of returns 403 before any Beneficiário lookup (FR-021, SC-009) in `backend/src/test/java/com/tbm/integration/TenantMembershipEnforcementTest.java` (depends on T045)
- [X] T051a [P] [US3] Integration test: after switching from Tenant A to Tenant B and back to Tenant A, Tenant A's Beneficiário listing is byte-for-byte unchanged from before the switch (FR-018, SC-004) in `backend/src/test/java/com/tbm/integration/TenantSwitchRoundTripTest.java` (depends on T045)
- [X] T052 [P] [US3] Frontend unit test: tenant switcher renders only the signed-in user's memberships in `frontend/tests/unit/TenantSwitcher.spec.js`

### Implementation for User Story 3

- [X] T053 [US3] Implement `TenantSwitcher` component (dropdown limited to `auth` store memberships, sets active tenant; all labels in Portuguese, per FR-025) in `frontend/src/components/TenantSwitcher.vue` (depends on T022)
- [X] T054 [US3] Implement `ActiveTenantBadge` component displaying the current tenant unambiguously (Portuguese label, per FR-025) in `frontend/src/components/ActiveTenantBadge.vue` (depends on T022)
- [X] T055 [US3] Wire tenant switch to refetch Beneficiário data and clear stale state on change in `frontend/src/stores/beneficiario.js` (depends on T047, T053)
- [X] T056 [US3] Mount `TenantSwitcher` and `ActiveTenantBadge` into the app shell in `frontend/src/App.vue` (depends on T053, T054)

**Checkpoint**: Tenant isolation is observable and verifiable end-to-end through the running UI.

---

## Phase 6: User Story 4 - Search, Filter, and Paginate the Beneficiário Listing (Priority: P4)

**Goal**: Users can narrow the active tenant's Beneficiário listing by Pessoa name and/or status, and browse results a page at a time.

**Independent Test**: With a tenant containing many Beneficiário records of varying names/statuses, apply a name filter, a status filter, both together, and page through results, confirming each returns the correct subset.

### Tests for User Story 4 ⚠️

- [X] T057 [P] [US4] Integration test: name filter, status filter, combined filter, and pagination (incl. page-beyond-last returns an empty result, not an error) in `backend/src/test/java/com/tbm/integration/BeneficiarioFilteringTest.java` (depends on T045)

### Implementation for User Story 4

- [X] T058 [US4] Extend `BeneficiarioRepository`/`BeneficiarioService` with Pessoa-`nome` and status filter predicates plus `Pageable` support (default size 20, sorted by Pessoa `nome`) in `backend/src/main/java/com/tbm/beneficiario/BeneficiarioRepository.java` and `BeneficiarioService.java` (depends on T044)
- [X] T059 [US4] Extend `BeneficiarioController` `GET` list with `pessoaNome`, `status`, `page`, `size` query params in `backend/src/main/java/com/tbm/beneficiario/BeneficiarioController.java` (depends on T058)
- [X] T060 [US4] Add nome/status filter inputs to `BeneficiarioListView` (Portuguese labels, per FR-025) in `frontend/src/views/BeneficiarioListView.vue` (depends on T048)
- [X] T061 [US4] Implement `PaginationControl` component and wire it into `BeneficiarioListView` (Portuguese labels, per FR-025) in `frontend/src/components/PaginationControl.vue` and `frontend/src/views/BeneficiarioListView.vue` (depends on T048)
- [X] T062 [US4] Add a clear empty-state display for no-match filters/pages (Portuguese message, per FR-025) in `frontend/src/views/BeneficiarioListView.vue` (depends on T060, T061)

**Checkpoint**: All four user stories are independently functional — the full feature is complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Delivery requirements that span all user stories (Constitution's Delivery & Documentation Requirements section)

- [X] T063 [P] Write repository root `README.md`: how to run via `docker-compose up`, architectural decisions (especially the multitenancy isolation strategy and why), and what would be done differently with more time
- [X] T064 [P] Verify the generated Swagger UI/OpenAPI output matches `specs/001-pessoa-beneficiario-crud/contracts/openapi.yaml`; adjust controller annotations as needed
- [X] T065 [P] Add CORS configuration allowing the frontend container's origin in `backend/src/main/java/com/tbm/config/SecurityConfig.java`
- [X] T066 Run `docker-compose up` from a clean state and execute every step of `specs/001-pessoa-beneficiario-crud/quickstart.md` end-to-end, including §9's UI-language spot-check (FR-025/SC-008) (depends on all prior phases)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories (auth, tenant resolution, error format, and Testcontainers base are shared by every story)
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - US1 (Pessoa) has no dependency on other stories
  - US2 (Beneficiário CRUD) depends on US1's `PessoaRepository` (T030) to validate Pessoa references, but is otherwise independently testable
  - US3 (Tenant switch/isolation) depends on US2's Beneficiário controller (T045) existing to prove isolation against
  - US4 (Filter/pagination) extends US2's Beneficiário repository/service/controller (T044/T045)
- **Polish (Phase 7)**: Depends on all four user stories being complete

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Entities before repositories before services before controllers
- Backend before the frontend pieces that consume it
- Story complete and checkpointed before moving to the next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel (T001–T004, T006–T007)
- Within Foundational, T010–T012 (entities), T018–T019 (config/validator), and T021, T024–T025 (frontend pieces) can run in parallel once their own dependencies are met
- Within each user story, all [P]-marked test tasks can run in parallel; entity/DTO tasks marked [P] can run in parallel
- US1 and, once T030 exists, the backend halves of US2 can be staffed in parallel by different developers; US3 and US4 both build on US2's controller and are best sequenced after it

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Unit test for @Cpf validator in backend/src/test/java/com/tbm/unit/CpfValidatorTest.java"
Task: "Integration test: Pessoa create/list/get/update, duplicate-CPF, invalid-CPF in backend/src/test/java/com/tbm/integration/PessoaIntegrationTest.java"
Task: "Integration test: Pessoa deletion blocked while referenced (generic message, no tenant names) in backend/src/test/java/com/tbm/integration/PessoaDeletionRestrictionTest.java"

# Launch independent implementation pieces together:
Task: "Implement Pessoa JPA entity in backend/src/main/java/com/tbm/pessoa/Pessoa.java"
Task: "Implement pessoaApi.js service in frontend/src/services/pessoaApi.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (global Pessoa registry)
4. **STOP and VALIDATE**: Run T026–T028 and confirm the Pessoa registry works standalone
5. Demo if ready — a working global registry is a legitimate MVP slice

### Incremental Delivery

1. Setup + Foundational → auth, tenant resolution, and error format ready
2. Add US1 (Pessoa) → validate independently → demo (MVP)
3. Add US2 (Beneficiário CRUD) → validate independently → demo (core value delivered)
4. Add US3 (tenant switch/isolation) → validate independently → demo (the platform's defining guarantee, now observable)
5. Add US4 (filter/pagination) → validate independently → demo (final, complete feature)
6. Phase 7 polish (README, OpenAPI check, CORS, full quickstart run) closes out delivery requirements

### Suggested Task Ownership Split (if staffed by more than one person)

1. Everyone completes Setup + Foundational together (it blocks everything)
2. Developer A: US1 backend + frontend
3. Developer B: US2 backend (after T030 lands) + frontend
4. Once US2's controller exists: Developer A or B picks up US3, the other picks up US4 — both can proceed in parallel
5. Either developer closes out Phase 7 polish

---

## Notes

- [P] tasks = different files, no unmet dependencies
- [Story] label maps each task to its user story for traceability
- Every user story is independently completable and testable per its Independent Test criterion in spec.md
- Verify tests fail before implementing (Tests sections are listed first in every story phase)
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently before moving on
- "Tenant" naming and the Portuguese-UI/entity-field requirement are both explicit per spec.md's 2026-08-15 clarifications — see the Naming Conventions section above
