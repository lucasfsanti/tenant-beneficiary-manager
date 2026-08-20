# Tasks: Transparent Tenant Scoping via Database Views

**Input**: Design documents from `/specs/007-tenant-transparent-views/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/beneficiario-api.md,
quickstart.md (all present)

**Tests**: This project's existing suite (`backend/src/test/java`) is integration/unit-test-heavy
by established convention, so test tasks are included throughout. Note on organization: the core
mechanism (database view + session-scoped tenant variable) makes the `Beneficiario` entity change
and the `BeneficiarioResponse` DTO change structurally inseparable — Hibernate's `ddl-auto:
validate` fails at boot if the entity still declares a `tenant_id` column the view doesn't have,
and `BeneficiarioService.toResponse()` won't compile if the DTO still expects a `tenantId`
argument the entity no longer provides. Both therefore land in Phase 2 (Foundational), matching
this repo's precedent in `specs/004-spring-security-admin-roles/tasks.md` ("this feature is an
internal mechanism swap ... User Story 1 carries essentially all the code changes"). User Story 1
and User Story 2 are each verified by their own dedicated tests in their own phase; User Story 3
(the audit log) is the one phase that adds genuinely new production code.

**Organization**: Tasks are grouped by user story per spec.md (US1 = P1 database enforcement,
US2 = P2 no tenant identifier in the API, US3 = P3 System Admin audit log).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every description below includes the exact file path(s) touched

## Path Conventions

Backend-only feature (no frontend changes — verified in plan.md that no frontend code reads a
`tenantId` field off a Beneficiário response). All paths are relative to the repository root,
under `backend/src/main/java/com/tbm/`, `backend/src/main/resources/db/changelog/`, and
`backend/src/test/java/com/tbm/`.

---

## Phase 1: Setup

**Purpose**: Establish the pre-change baseline this feature's data-preservation and
zero-regression success criteria (SC-003, and the general "existing tests stay green" bar) are
measured against.

- [X] T001 Run the full backend test suite (`cd backend && ./mvnw test`) and confirm it is
      currently all-green before any change.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Swap Beneficiário's storage/access mechanism from a raw table with an app-code
`tenant_id` filter to a tenant-filtering database view driven by a per-transaction session
variable. This is the one atomic change every user story depends on — the codebase must compile
and the existing test suite must pass again by the end of this phase.

**⚠️ CRITICAL**: No user-story-specific work can begin until this phase is complete.

- [X] T002 Add Liquibase changeset `backend/src/main/resources/db/changelog/006-tenant-view-and-audit-log.sql`:
      `ALTER TABLE beneficiario ALTER COLUMN tenant_id SET DEFAULT
      NULLIF(current_setting('app.tenant_id', true), '')::uuid;` (no table rename — the base
      table keeps its existing name), then `CREATE VIEW vw_beneficiario AS SELECT id, pessoa_id,
      matricula, tipo, status, data_adesao, created_at, updated_at FROM beneficiario WHERE
      tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid;` (the `NULLIF(...,
      '')` wrapper is required, not just `missing_ok=true` alone — research.md §2 explains the
      empty-string placeholder gotcha this works around), then `CREATE TABLE
      tenant_access_audit_log (id UUID PRIMARY KEY, admin_user_id UUID NOT NULL REFERENCES
      app_user (id), target_tenant_id UUID NOT NULL REFERENCES tenant (id), accessed_at
      TIMESTAMPTZ NOT NULL DEFAULT now());` (research.md §2, §5, §7; data-model.md).
- [X] T003 Register the new changeset by adding an `include` entry for
      `db/changelog/006-tenant-view-and-audit-log.sql` to
      `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (depends on T002).
- [X] T004 [P] Create `TenantSessionContext` in
      `backend/src/main/java/com/tbm/security/TenantSessionContext.java`: an injectable
      `@Component` with `void apply(UUID tenantId)` that runs `SELECT set_config('app.tenant_id',
      :tenantId, true)` via the injected `EntityManager`, letting any exception propagate
      unchanged (research.md §1, §3, §4).
- [X] T005 [P] In `backend/src/main/java/com/tbm/beneficiario/Beneficiario.java`: change
      `@Table(name = "beneficiario")` to `@Table(name = "vw_beneficiario")`, and remove the
      `tenantId` field, `getTenantId()`, and `setTenantId()` (data-model.md; research.md §2).
- [X] T006 Update `backend/src/main/java/com/tbm/beneficiario/BeneficiarioRepository.java`
      (depends on T005): remove `findByIdAndTenantId`, `existsByTenantId`,
      `existsByTenantIdAndMatricula`, `existsByTenantIdAndMatriculaAndIdNot`; rely on the
      inherited `findById`/`count`; add `existsByMatricula(String matricula)` and
      `existsByMatriculaAndIdNot(String matricula, UUID id)`; update the `search(...)` `@Query` to
      drop the `AND b.tenantId = :tenantId` predicate and the `tenantId` parameter.
- [X] T007 Update `backend/src/main/java/com/tbm/beneficiario/BeneficiarioService.java` (depends
      on T004, T006): make `activeTenantId()` also call `tenantSessionContext.apply(tenantId)`
      before returning; stop calling `beneficiario.setTenantId(tenantId)` in `create()` (the
      base-table `DEFAULT` now stamps it); change `findOrThrow` to
      `beneficiarioRepository.findById(id)`; change `update()`'s uniqueness check to
      `existsByMatriculaAndIdNot(input.matricula(), id)` (drop the `UUID tenantId =
      beneficiario.getTenantId();` line); change `create()`'s uniqueness check to
      `existsByMatricula(input.matricula())`.
- [X] T008 Update `backend/src/main/java/com/tbm/tenant/TenantService.java` `delete()` (depends
      on T004, T006): replace `beneficiarioRepository.existsByTenantId(tenantId)` with
      `tenantSessionContext.apply(tenantId); boolean hasBeneficiarios =
      beneficiarioRepository.count() > 0;` (research.md §6).
- [X] T009 [P] Remove the `tenantId` field from
      `backend/src/main/java/com/tbm/beneficiario/dto/BeneficiarioResponse.java`
      (contracts/beneficiario-api.md).
- [X] T010 Update `toResponse()` in
      `backend/src/main/java/com/tbm/beneficiario/BeneficiarioService.java` (depends on T007,
      T009): drop the `beneficiario.getTenantId()` argument from the `BeneficiarioResponse`
      constructor call.
- [X] T011 Fix the now-invalid assertion in
      `backend/src/test/java/com/tbm/integration/BeneficiarioCreationTest.java:40`
      (`response.getBody().tenantId()`) — remove it (depends on T009).
- [X] T012 Run `cd backend && ./mvnw test` and confirm the full suite compiles and passes again
      against the view-backed model (depends on T003, T008, T010, T011). As part of this, add or
      extend a test to assert `SELECT count(*) FROM beneficiario` (seeded via
      `002-seed-demo-data.sql`/demo profile) is unchanged after the new changeset runs versus the
      pre-changeset seed count — an automated check for FR-010/SC-003, since the migration itself
      touches no existing rows (research.md §5).

**Checkpoint**: Foundation ready — Beneficiário reads/writes now go through the tenant-filtering
view, the entity/DTO no longer carry `tenantId`, and the existing suite is green.

---

## Phase 3: User Story 1 - Tenant isolation is enforced by the database itself (Priority: P1) 🎯 MVP

**Goal**: Prove tenant filtering holds at the database layer, independent of application code.

**Independent Test**: Query the database directly with and without the session tenant variable
set, and confirm application-level cross-tenant access attempts fail, per quickstart.md steps
2–3.

- [X] T013 [P] [US1] New test `backend/src/test/java/com/tbm/integration/DatabaseEnforcedIsolationTest.java`
      (extends `AbstractIntegrationTest`, uses `@Autowired DataSource` or `EntityManager` for raw
      SQL): (a) with no `app.tenant_id` set in a transaction, `SELECT count(*) FROM
      vw_beneficiario` returns `0` even though `SELECT count(*) FROM beneficiario` (the base
      table) shows seeded rows across every tenant; (b) inside a transaction with `SELECT
      set_config('app.tenant_id', '<Tenant Alfa id>', true)`, querying `vw_beneficiario` returns
      only Tenant Alfa's rows; (c) after that transaction commits, a fresh transaction with no
      context set again returns `0` from `vw_beneficiario` (proves no leak across the pooled
      connection) (quickstart.md step 3; spec Acceptance Scenarios 1–2, Edge Case "pooled database
      connection").
- [X] T014 [P] [US1] New test `backend/src/test/java/com/tbm/security/TenantSessionContextTest.java`:
      (a) `apply(tenantId)` issues a `set_config` call with the given tenant id and `is_local =
      true`; (b) when the underlying `EntityManager`/native query throws, the exception propagates
      out of `apply()` unchanged (FR-003 fail-closed, research.md §4).
- [X] T015 [P] [US1] `backend/src/test/java/com/tbm/integration/TenantIsolationTest.java`
      already exists (feature 001/005) with three tests covering GET/PUT/DELETE returning `404`
      for a record belonging to a tenant other than the active one (spec Acceptance Scenario 2,
      FR-006) — extend it, don't replace it: add one new test asserting `GET /api/beneficiarios`
      with `X-Tenant-Id` set to a tenant the user holds NO membership in at all returns `403`
      (SC-004; this exercises the existing, unchanged `TenantContextFilter` rejection under the
      new view-backed model).

**Checkpoint**: Database-level enforcement is verified independently of application filtering
code.

---

## Phase 4: User Story 2 - API and entity model no longer carry a tenant identifier (Priority: P2)

**Goal**: Confirm the tenant identifier is gone from both the persistence model (done in Phase 2)
and the API's visible contract.

**Independent Test**: Call Beneficiário endpoints and inspect the raw JSON response body, per
quickstart.md step 1.

- [X] T016 [P] [US2] New test `backend/src/test/java/com/tbm/integration/BeneficiarioResponseContractTest.java`:
      call `GET /api/beneficiarios`, `GET /api/beneficiarios/{id}`, `POST /api/beneficiarios`, and
      `PUT /api/beneficiarios/{id}` deserializing into `Map`/`JsonNode` (not the typed DTO, so the
      assertion can't pass trivially) and assert none of the returned objects contain a
      `tenantId` key (contracts/beneficiario-api.md "After" shape, SC-001).
- [X] T017 [US2] Against the running `docker-compose` stack, run `curl -s
      localhost:8080/v3/api-docs | jq '.components.schemas.BeneficiarioResponse.properties'` and
      confirm `tenantId` is absent from the output (FR-012) — no code change expected (springdoc
      regenerates from the DTO in T009), this is a verification step only.

**Checkpoint**: The tenant identifier is confirmed absent from both storage and the API surface.

---

## Phase 5: User Story 3 - Support staff can still investigate specific tenants (Priority: P3)

**Goal**: Preserve the existing System Admin cross-tenant bypass under the new view-based model,
and record every use of it.

**Independent Test**: A System Admin can still access a tenant they don't belong to; a non-admin
cannot; every successful bypass produces an audit row, per quickstart.md steps 4–5.

- [X] T018 [P] [US3] Create entity `backend/src/main/java/com/tbm/security/TenantAccessAuditLog.java`
      mapped to `tenant_access_audit_log` (`id`, `adminUserId` → `admin_user_id`,
      `targetTenantId` → `target_tenant_id`, `accessedAt` → `accessed_at`) (data-model.md).
- [X] T019 [P] [US3] Create `backend/src/main/java/com/tbm/security/TenantAccessAuditLogRepository.java`
      (`JpaRepository<TenantAccessAuditLog, UUID>`) (depends on T018).
- [X] T020 [US3] Update `backend/src/main/java/com/tbm/security/TenantContextFilter.java` (depends
      on T019): add a `TenantAccessAuditLogRepository` constructor parameter/field; at the point
      the filter already evaluates `isSystemAdmin && !membershipRepository.existsByUser_IdAndTenant_Id(...)`
      and decides to allow the bypass, save one `TenantAccessAuditLog` row
      (`adminUserId = principal.userId()`, `targetTenantId = tenantId`) before calling
      `filterChain.doFilter(...)` (research.md §7, FR-013).
- [X] T021 [US3] Update `backend/src/main/java/com/tbm/config/SecurityConfig.java:77` (depends on
      T020): pass the `TenantAccessAuditLogRepository` bean into
      `new TenantContextFilter(membershipRepository, objectMapper, auditLogRepository)`.
- [X] T022 [US3] Update `backend/src/test/java/com/tbm/security/TenantContextFilterTest.java`
      (depends on T020): fix the existing `new TenantContextFilter(...)` construction to pass a
      mocked `TenantAccessAuditLogRepository`; add a test asserting `save()` is called with the
      admin's id and the target tenant id only when the bypass branch is taken, and never on the
      normal (in-membership) branch.
- [X] T023 [US3] Extend `backend/src/test/java/com/tbm/integration/SystemAdminBeneficiarioAccessTest.java`
      (depends on T020): after the existing System Admin flow against Tenant Alfa, autowire
      `TenantAccessAuditLogRepository` and assert exactly one row exists with
      `targetTenantId = Tenant Alfa` and `adminUserId` matching the seeded System Admin; add a
      second test where `User 1 - NORMAL` attempts the same cross-tenant request against a tenant
      they don't belong to, asserting `403` and that no audit row was created (spec Acceptance
      Scenarios 1–3, FR-013, SC-005, SC-006).

**Checkpoint**: System Admin cross-tenant access still works end-to-end and every use of it is
audited; non-admins are still rejected and produce no audit trail.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Bring project documentation in line with the new mechanism and do a final
end-to-end pass.

- [X] T024 [P] Update the "Isolamento multitenant" section of `README.md` (currently describes
      `BeneficiarioRepository` methods like `findByIdAndTenantId` and app-code filtering) to
      describe the view + per-transaction `set_config` mechanism and the System Admin audit log,
      per Constitution's Delivery & Documentation Requirements.
- [X] T025 Walk through `quickstart.md` end-to-end against a running `docker-compose up` stack
      (all 6 scenarios) and confirm every expected outcome holds.
- [X] T026 Run `cd backend && ./mvnw test` one final time and confirm the full suite (including
      all tasks above) is green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
- **User Stories (Phase 3–5)**: All depend on Foundational phase completion. Each is
  independently testable per its own Independent Test above; they touch disjoint files except
  where noted, so they can proceed in any order or in parallel.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational — no dependency on US2/US3.
- **User Story 2 (P2)**: Can start after Foundational — no dependency on US1/US3 (its underlying
  code change already landed in Foundational; this phase is verification-only).
- **User Story 3 (P3)**: Can start after Foundational — no dependency on US1/US2. Adds genuinely
  new files (`TenantAccessAuditLog`, its repository) not touched by any other story.

### Within Each Phase

- Foundational: T002→T003; T004/T005 parallel; T006 after T005; T007 after T004+T006; T008 after
  T004+T006; T009 parallel with T005–T008; T010 after T007+T009; T011 after T009; T012 last.
- US1: T013/T014/T015 are all independent new test files — fully parallel.
- US2: T016 then T017 (T017 is a manual check best run after the automated test passes).
- US3: T018→T019; T020 after T019; T021 after T020; T022 after T020; T023 after T020.

### Parallel Opportunities

- T004 and T005 (Foundational, different files).
- T009 alongside T005–T008 (Foundational, different file, no shared dependency).
- T013, T014, T015 (all of US1 — three independent new test files).
- T018 and T019 could be done as one combined edit, but are listed separately since they're
  different files.
- US1, US2, and US3 phases themselves can be worked in parallel once Foundational is done, since
  they touch disjoint files (US1: new test files only; US2: new test file + manual check; US3:
  `TenantContextFilter`, `SecurityConfig`, new audit files).

---

## Parallel Example: Foundational Phase

```bash
# After T002/T003 (migration) land, these can run together:
Task: "Create TenantSessionContext in backend/src/main/java/com/tbm/security/TenantSessionContext.java"
Task: "Remove tenantId field from backend/src/main/java/com/tbm/beneficiario/Beneficiario.java"
Task: "Remove tenantId field from backend/src/main/java/com/tbm/beneficiario/dto/BeneficiarioResponse.java"
```

## Parallel Example: User Story 1

```bash
Task: "DatabaseEnforcedIsolationTest.java — direct SQL proof of view-level filtering"
Task: "TenantSessionContextTest.java — set_config call shape + fail-closed propagation"
Task: "TenantIsolationTest.java — cross-tenant 404 via the API"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — this is where the actual mechanism change happens).
3. Complete Phase 3: User Story 1 (proves the database-enforcement guarantee — the core of the
   request).
4. **STOP and VALIDATE**: run T013–T015 and confirm they pass.

### Incremental Delivery

1. Setup + Foundational → the mechanism is live and the existing suite is green.
2. Add User Story 1 → database-enforcement proven independently (MVP!).
3. Add User Story 2 → API contract change confirmed.
4. Add User Story 3 → System Admin bypass preserved and audited.
5. Polish → docs updated, full quickstart walked, final regression run.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Most of this feature's actual code change is concentrated in Phase 2 (Foundational) because the
  view-backed entity mechanism is a single atomic swap — this is expected for a mechanism-swap
  feature (see this repo's own precedent in specs/004) and does not indicate the user-story
  breakdown is wrong.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently.

---

## Phase 7: Convergence

- [X] T027 CRITICAL: fix `TenantContextFilter` so a System Admin bypass against a
      **nonexistent** target tenant returns a clean `ProblemDetail` rejection instead of an
      uncaught `ConstraintViolationException` (FK violation on
      `tenant_access_audit_log_target_tenant_id_fkey`) that currently surfaces to the client
      as a misleading `401 "Não autenticado"` — validate the tenant exists before granting the
      bypass / writing the audit row per FR-013 / FR-011 (contradicts)
- [X] T028 Extend `TenantService.delete()`'s existing-references guard to also check
      `tenant_access_audit_log` for rows referencing the target tenant, so deleting a tenant
      with historical audit records (but no Beneficiários/memberships) produces the existing
      `BusinessRuleException` message instead of an uncaught FK violation per plan.md (missing)
- [X] T029 Add an integration-level test exercising FR-003's fail-closed abort-and-500 behavior
      through a real HTTP request (not just `TenantSessionContextTest`'s mocked `EntityManager`),
      matching the "new failure mode" documented in contracts/beneficiario-api.md per FR-003
      (partial)
