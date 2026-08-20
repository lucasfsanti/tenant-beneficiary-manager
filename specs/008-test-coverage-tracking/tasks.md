# Tasks: Test Coverage Tracking

**Input**: Design documents from `/specs/008-test-coverage-tracking/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md (all present; no data-model.md or
contracts/ — this feature introduces no entity or API change)

**Tests**: This feature *is* test-writing, so every non-setup task either adds a test or the
tooling to report on tests. Task descriptions below reference exact file/line findings from
`research.md` §3 (backend) rather than re-deriving them.

**Organization**: Tasks are grouped by user story per spec.md (US1 = P1 backend coverage,
US2 = P2 frontend coverage, US3 = P3 documented exclusions). There is no Foundational phase:
backend and frontend tooling are fully independent (different files, different ecosystems, no
shared prerequisite), so each stack's tooling setup is the first task of its own user-story
phase instead of a shared blocking phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every description below includes the exact file path(s) touched

## Path Conventions

Backend paths are relative to the repo root under `backend/`; frontend paths under `frontend/`.

---

## Phase 1: Setup

**Purpose**: Confirm both test suites are currently green before adding coverage tooling or new
tests, so any later failure is attributable to this feature's own changes.

- [X] T001 [P] Run `cd backend && mvn test` and confirm all tests currently pass (127 tests per
      research.md's baseline measurement).
- [X] T002 [P] Run `cd frontend && npm test` and confirm all tests currently pass.

---

## Phase 2: User Story 1 - Backend coverage is visible and complete (Priority: P1) 🎯 MVP

**Goal**: `mvn test` produces a coverage report automatically, and the backend reaches 100% line
and branch coverage except for the one documented exclusion.

**Independent Test**: Run `cd backend && mvn test`, open `target/site/jacoco/index.html`, confirm
100% except `TenantBeneficiaryManagerApplication` (quickstart.md steps 1–3).

- [X] T003 [P] [US1] Add the JaCoCo plugin to `backend/pom.xml`: `prepare-agent` execution plus a
      `report` execution explicitly bound to the `test` phase (not JaCoCo's default `verify`, so
      plain `mvn test` produces the report — research.md §1), with
      `TenantBeneficiaryManagerApplication.class` excluded via an inline XML comment explaining
      why (framework-invoked `main()`, never exercised by application tests — research.md §2). Do
      **not** add a `jacoco:check` execution (research.md §7 — keeps coverage report-only, per
      FR-004).
- [X] T004 [P] [US1] Extend `backend/src/test/java/com/tbm/integration/TenantCrudTest.java`: add
      a test asserting `GET`/`PUT`/`DELETE` against an unknown Tenant id returns `404` — closes
      `TenantService.java:86`'s uncovered `findOrThrow` lambda (research.md §3).
- [X] T005 [P] [US1] Extend
      `backend/src/test/java/com/tbm/integration/MembershipManagementTest.java`: add a test
      asserting `POST /api/tenants/{unknown-id}/members` returns `404` — closes
      `MembershipService.java:46`'s uncovered lambda (research.md §3).
- [X] T006 [P] [US1] Extend
      `backend/src/test/java/com/tbm/integration/SystemAdminBeneficiarioAccessTest.java`: add a
      test where a System Admin who is **also** a genuine member of the target tenant accesses
      Beneficiário data, and assert **no** new `tenant_access_audit_log` row is created (it isn't
      a bypass) — closes the missed branch on `TenantContextFilter.java:110`'s compound
      `isSystemAdmin && !isMember` condition (research.md §3). Requires seeding/using a tenant
      where the seeded System Admin has been granted membership, or adding one via the existing
      membership-management endpoints within the test.
- [X] T007 [P] [US1] Create
      `backend/src/test/java/com/tbm/security/TenantAuthorizationTest.java`: unit tests
      constructing `TenantAuthorization` directly and setting
      `SecurityContextHolder`'s authentication to (a) `null` and (b) a non-`JwtPrincipal`
      principal, asserting `isTenantAdmin(...)` returns `false` in both cases — mirrors the
      existing pattern in `TenantContextFilterTest.passesThroughWhenThePrincipalIsNotAJwtPrincipal`
      — closes `TenantAuthorization.java:24-26` (research.md §3).
- [X] T008 [P] [US1] Extend the bypass-audit test in
      `backend/src/test/java/com/tbm/security/TenantContextFilterTest.java`
      (`savesAnAuditRecordWhenASystemAdminUsesTheCrossTenantBypass`): add
      `assertThat(saved.getId()).isNotNull()` to the existing `ArgumentCaptor`-based assertions —
      closes the unused `TenantAccessAuditLog.getId()` getter at line 32 (research.md §3).
- [X] T009 [US1] Run `cd backend && mvn test` (depends on T003–T008) and open
      `target/site/jacoco/index.html`; confirm overall line and branch coverage is 100% except for
      the documented `TenantBeneficiaryManagerApplication` exclusion (quickstart.md steps 1–2).
      Then run `mvn test; echo "exit code: $?"` and confirm `0` — coverage must never affect the
      command's pass/fail outcome — and confirm no `jacoco:check` execution exists
      (`grep -n "jacoco:check\|<goal>check</goal>" backend/pom.xml` returns nothing;
      quickstart.md step 3, FR-004).

**Checkpoint**: Backend coverage is reported automatically and at target — deliverable and
demoable on its own.

---

## Phase 3: User Story 2 - Frontend coverage is visible and complete (Priority: P2)

**Goal**: `npm test` produces a coverage report automatically, and the frontend reaches 100%
coverage except for the one documented exclusion.

**Independent Test**: Run `cd frontend && npm test`, open the generated coverage report, confirm
100% except `src/main.js` and that every previously-untested file now shows real coverage
(quickstart.md steps 4–5).

- [X] T010 [US2] Add `@vitest/coverage-v8` as a devDependency in `frontend/package.json`, and in
      `frontend/vite.config.js`'s existing `test` block set `coverage.provider: 'v8'`,
      `coverage.enabled: true` (so the existing `npm test` — `vitest run` — always collects
      coverage, no new script needed — research.md §4, §5), and exclude `src/main.js` via an
      inline comment explaining why (framework bootstrap/mount, mirrors the backend's `main()`
      exclusion — research.md §6). Do **not** set `coverage.thresholds` (research.md §7 — keeps
      coverage report-only, per FR-004).
- [X] T011 [P] [US2] Create `frontend/tests/unit/App.spec.js`: test role-based nav link
      visibility (`auth.isSystemAdmin`, `auth.isTenantAdminFor`) and that `handleLogout` calls
      `auth.logout()` and navigates to `/login`.
- [X] T012 [P] [US2] Create `frontend/tests/unit/ActiveTenantBadge.spec.js`.
- [X] T013 [P] [US2] Create `frontend/tests/unit/ErrorBanner.spec.js`.
- [X] T014 [P] [US2] Create `frontend/tests/unit/PaginationControl.spec.js`.
- [X] T015 [P] [US2] Create `frontend/tests/unit/BeneficiarioListView.spec.js`.
- [X] T016 [P] [US2] Create `frontend/tests/unit/BeneficiarioFormView.spec.js`.
- [X] T017 [P] [US2] Create `frontend/tests/unit/PessoaListView.spec.js`.
- [X] T018 [P] [US2] Create `frontend/tests/unit/PessoaFormView.spec.js`.
- [X] T019 [P] [US2] Create `frontend/tests/unit/LoginView.spec.js`.
- [X] T020 [P] [US2] Create `frontend/tests/unit/SystemAdminsView.spec.js`.
- [X] T021 [US2] Run `cd frontend && npm test` (depends on T010–T020), open the generated
      coverage report, and identify any lines/branches still uncovered — in the Pinia stores
      (`src/stores/{auth,beneficiario,pessoa,tenant}.js`) or API service modules
      (`src/services/{api,beneficiarioApi,pessoaApi,problemDetail,tenantAdminApi}.js`) not already
      exercised incidentally by the view/component tests above. Unlike the backend, these exact
      gaps are unknowable until the coverage provider is actually installed (research.md §3 vs.
      the frontend note) — add whatever store/service-level tests are needed to close what this
      run actually reports.
- [X] T022 [US2] Re-run `cd frontend && npm test` (depends on T021) and confirm overall coverage
      is 100% except for the documented `src/main.js` exclusion (quickstart.md steps 4–5). Then
      run `npm test; echo "exit code: $?"` and confirm `0` — coverage must never affect the
      command's pass/fail outcome — and confirm no `coverage.thresholds` is configured
      (`grep -n "thresholds" frontend/vite.config.js` returns nothing; quickstart.md step 6,
      FR-004).

**Checkpoint**: Frontend coverage is reported automatically and at target — deliverable and
demoable on its own, independent of User Story 1.

---

## Phase 4: User Story 3 - Every exclusion is documented, not silent (Priority: P3)

**Goal**: Confirm the only two coverage exclusions in the whole project are the backend and
frontend bootstrap entry points, and both carry a written rationale.

**Independent Test**: List every exclusion configured in `backend/pom.xml` and
`frontend/vite.config.js` and confirm each has an accompanying reason (quickstart.md step 7).

- [X] T023 [US3] Verify (depends on T003, T010): `grep -n -B2 "TenantBeneficiaryManagerApplication"
      backend/pom.xml` and `grep -n -B2 "main.js" frontend/vite.config.js` each show the
      exclusion accompanied by an explanatory comment, per spec Acceptance Scenario 1; and confirm
      no other file/class is excluded without one, per spec Acceptance Scenario 2 (each exclusion
      narrow — a specific file, not a broad package/directory).

**Checkpoint**: The 100% figure on both reports is honest and auditable — every gap in the
number is either closed by a real test or explained in the configuration itself.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Make the new capability discoverable and do a final end-to-end pass.

- [X] T024 [P] Update the "Rodando os testes localmente" section of `README.md` to mention that
      running the existing test commands now also produces coverage reports, and where to find
      them (`backend/target/site/jacoco/index.html`, `frontend/coverage/index.html` or
      equivalent), per Constitution's Delivery & Documentation Requirements.
- [X] T025 Run `cd backend && mvn test` and `cd frontend && npm test` one final time (depends on
      all prior tasks) and confirm both remain green and both coverage reports show 100% minus
      the two documented exclusions (quickstart.md, full walkthrough). Also confirm
      `ls .github/workflows/ 2>&1` still reports "No such file or directory" — this feature must
      not have introduced a CI pipeline (quickstart.md step 8, FR-009). Finally, skim the tests
      added in T004–T008 and T011–T021 and spot-check that each asserts real, meaningful
      application behavior (e.g., a response status, a returned value, a call to a mock) rather
      than merely executing a line/branch with no assertion, solely to raise the percentage
      (FR-008).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **User Story 1 (Phase 2)**: Depends on Setup (T001). No dependency on US2/US3.
- **User Story 2 (Phase 3)**: Depends on Setup (T002). No dependency on US1/US3 — fully
  independent stack.
- **User Story 3 (Phase 4)**: Depends on the exclusions existing (T003 from US1, T010 from US2) —
  the only cross-story dependency in this feature, and it's a read-only verification, not new
  code.
- **Polish (Phase 5)**: Depends on all desired user stories being complete.

### Within Each Phase

- US1: T003–T008 are all independent (different files) — fully parallel; T009 depends on all six.
- US2: T010 (tooling) and T011–T020 (new spec files) are independent of each other and of one
  another — fully parallel; T021 depends on T010–T020; T022 depends on T021.
- US3: T023 depends on T003 and T010 (needs both exclusions to already exist).

### Parallel Opportunities

- T001 and T002 (Setup, different stacks).
- T003–T008 (all of US1's implementation — six independent files).
- T010–T020 (US2's tooling setup plus all ten new spec files — eleven independent files).
- US1 and US2 can be worked in parallel by different people/sessions from the start, since they
  touch entirely disjoint files across two different stacks.

---

## Parallel Example: User Story 1

```bash
Task: "Add JaCoCo plugin to backend/pom.xml"
Task: "Add unknown-Tenant-id 404 test to TenantCrudTest.java"
Task: "Add unknown-Tenant-id 404 test to MembershipManagementTest.java"
Task: "Add admin-who-is-also-a-member test to SystemAdminBeneficiarioAccessTest.java"
Task: "Create TenantAuthorizationTest.java"
Task: "Add getId() assertion to TenantContextFilterTest.java"
```

## Parallel Example: User Story 2

```bash
Task: "Add @vitest/coverage-v8 and configure vite.config.js"
Task: "Create App.spec.js"
Task: "Create ActiveTenantBadge.spec.js"
Task: "Create ErrorBanner.spec.js"
Task: "Create PaginationControl.spec.js"
Task: "Create BeneficiarioListView.spec.js"
Task: "Create BeneficiarioFormView.spec.js"
Task: "Create PessoaListView.spec.js"
Task: "Create PessoaFormView.spec.js"
Task: "Create LoginView.spec.js"
Task: "Create SystemAdminsView.spec.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: User Story 1 — backend coverage reported and at target.
3. **STOP and VALIDATE**: run T009, confirm the report shows 100% minus the one exclusion.

### Incremental Delivery

1. Setup → both suites confirmed green.
2. Add User Story 1 → backend coverage reported and at target (MVP!).
3. Add User Story 2 → frontend coverage reported and at target.
4. Add User Story 3 → both exclusions confirmed documented, nothing silent.
5. Polish → README updated, full final regression run on both stacks.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Backend gaps (T004–T008) were measured, not guessed — research.md §3 has the exact
  file/line/branch for each, from a real JaCoCo run against the current, fully-passing suite.
- Frontend gaps for the ten new spec files (T011–T020) are known in advance (zero existing
  coverage); the store/service-level gaps (T021) are not knowable until the coverage provider is
  actually installed — that task is deliberately a "measure, then close" step rather than a
  pre-enumerated list.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently.
