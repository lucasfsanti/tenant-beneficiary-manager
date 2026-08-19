---

description: "Task list template for feature implementation"
---

# Tasks: User Self-Registration (Bootstrap Entrypoint)

**Input**: Design documents from `/specs/006-user-self-registration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/README.md, quickstart.md (all present)

**Tests**: This feature's core guarantees (FR-003/005/011, Edge Cases' race condition) are not
credibly verifiable by manual inspection alone, so test tasks are included as first-class,
essential work — not optional polish.

**Testing architecture note** (read before Phase 3): `AbstractIntegrationTest`'s shared,
JVM-lifetime, static Testcontainers Postgres container never sets `@ActiveProfiles`, so it
always boots with the `demo` profile active and Liquibase always seeds demo data before any
test method in the ~12 existing integration test classes runs. That means the *shared* container
is never genuinely empty by the time a test can observe it — which is exactly right for testing
User Story 2 (registering against an already-populated platform) but cannot exercise User Story
1's "the very first account on an empty platform" scenario or the Edge Cases concurrency race.
Those two need their own, separately-provisioned Testcontainers Postgres instance running with
feature 005's `no-demo` profile, so `app_user` starts genuinely empty. This is why Phase 3 below
splits backend coverage across a dedicated isolated-container test class (T009) and a fast
Mockito-based unit test of the decision logic (T010), while Phase 4/5's backend coverage (T011,
T014) safely reuses the always-pre-seeded shared container.

**Organization**: All three user stories in spec.md are P1 and share one implementation surface
(the single `register()` code path) — they cannot be delivered as separable code slices the way
independent features usually are. Phase 2 (Foundational) therefore builds the complete
mechanism, end to end, backend and frontend together; each user-story phase is primarily about
the specific, dedicated tests that prove that one already-built mechanism satisfies that story's
acceptance scenarios — mirroring how feature 005 handled its own single shared cross-cutting
mechanism (migration split + optional seeding, built once in Foundational, verified across later
phases).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every description below includes the exact file path(s) touched

## Path Conventions

Web app (backend + frontend), matching every prior feature in this repo. Backend paths under
`backend/src/main/java/com/tbm/user/`, `backend/src/test/java/com/tbm/{integration,unit}/`.
Frontend paths under `frontend/src/`, `frontend/tests/unit/`. Plus `README.md` at the repo root.

---

## Phase 1: Setup

**Purpose**: Establish the pre-change baseline this feature's zero-regression expectation is
measured against.

- [X] T001 Run the full backend test suite (`cd backend && mvn clean test
      -Dnet.bytebuddy.experimental=true`, with `JAVA_HOME=~/.local/opt/jdk-21.0.4+7`) and the full
      frontend test suite (`cd frontend && npm test`), and confirm both are currently all-green
      before any change. **Confirmed**: backend 102/102 green, frontend 10/10 green.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the complete registration mechanism — backend endpoint and frontend page —
since User Stories 1–3 are three acceptance angles on this one mechanism, not separable slices
of it (see Organization note above).

**⚠️ CRITICAL**: No user-story verification work can begin until this phase is complete.

- [X] T002 [P] Create `backend/src/main/java/com/tbm/user/dto/RegisterRequest.java`: a record with
      `@NotBlank String username` and `@NotBlank String password`, mirroring
      `LoginRequest.java`'s shape exactly. Deliberately no role/admin field of any kind
      (research.md §4, spec FR-011).
- [X] T003 [P] Add a repository method to `backend/src/main/java/com/tbm/user/AppUserRepository.java`
      that, within the caller's transaction, first acquires a PostgreSQL transaction-scoped
      advisory lock (`SELECT pg_advisory_xact_lock(<a fixed, documented constant>)` via a native
      query) and then reports whether any `app_user` row currently exists — research.md §1. Document
      inline why an advisory lock is used instead of `SELECT ... FOR UPDATE` (an empty table has no
      rows to lock). **Implemented as `lockAndCheckAnyAccountExists()`**, a single native query
      (`WITH lock_acquired AS (SELECT pg_advisory_xact_lock(727310147)) SELECT EXISTS (...) FROM
      lock_acquired`) so the lock-then-check stays one atomic statement; lock key documented inline
      as a codebase-unique constant (confirmed via grep — no other advisory lock exists).
- [X] T004 Implement `register(String username, String password)` in
      `backend/src/main/java/com/tbm/user/AuthService.java`, inside a single `@Transactional`
      method: reject with the existing `ConflictException` if `username` already exists (spec
      FR-002); otherwise call T003's helper to decide `isSystemAdmin` (true only if no account
      existed yet, spec FR-003/FR-005); hash the password with the existing `PasswordEncoder`
      (spec FR-007); persist a new `AppUser` with zero `user_tenant_membership` rows; issue no
      token (spec FR-009, Assumptions). Depends on T002, T003.
- [X] T005 Add `POST /api/auth/register` to `backend/src/main/java/com/tbm/user/AuthController.java`:
      no `@SecurityRequirement` (public), `@Valid @RequestBody RegisterRequest`, calls
      `authService.register(...)`, `@ResponseStatus(HttpStatus.NO_CONTENT)` (research.md §3/§4,
      contracts/README.md). Depends on T004. **Verified**: `mvn compile` succeeds.
- [X] T006 [P] Add a `register(username, password)` action to `frontend/src/stores/auth.js`,
      mirroring `login()`'s try/catch and `this.error` handling, but calling
      `POST /api/auth/register` and returning a plain success/failure boolean — it must NOT set
      `this.token`/`this.user` or touch `localStorage` (spec FR-009, Assumptions: no session is
      established by registration).
- [X] T007 Create `frontend/src/views/CreateUserView.vue`, mirroring `LoginView.vue`'s
      template/script/style structure (username + password fields, `ErrorBanner`, submit button
      with a loading state) and calling `auth.register(...)` from T006 on submit; on success,
      `router.push('/login')` (spec FR-009). No role-selection control anywhere on the page — there
      is nothing to choose (spec FR-003/FR-005). Depends on T006.
- [X] T008 Add a public route for `CreateUserView.vue` to `frontend/src/router/index.js`
      (`meta: { public: true }`, matching the existing `/login` route's pattern exactly). Depends
      on T007. **Route**: `/criar-conta` (matches the project's Portuguese-language URL/label
      convention — every other route already uses Portuguese, e.g. `/pessoas`, `/tenants`).
      **Verified**: `eslint` on all 3 new/changed frontend files shows only the same pre-existing
      style warnings `LoginView.vue` already has (0 errors).

**Checkpoint**: The full registration mechanism exists end-to-end (backend endpoint + frontend
page), with the role-decision logic in place. Nothing about it has been proven correct by a
dedicated test yet — that's every phase below.

---

## Phase 3: User Story 1 - Bootstrap the very first account (Priority: P1) 🎯 MVP

**Goal**: Prove that on a genuinely empty platform, the very first registration is automatically
granted System Admin, with no role choice presented (spec FR-003).

**Independent Test**: Per spec.md — on a freshly migrated, completely empty database, register
without being logged in and confirm the resulting account can log in and perform a System-Admin-only
action.

### Tests for User Story 1

- [X] T009 [US1] Create `backend/src/test/java/com/tbm/integration/UserSelfRegistrationBootstrapTest.java`:
      does **not** extend `AbstractIntegrationTest` — instead starts its own, class-scoped
      Testcontainers Postgres container (`@Testcontainers`/`@Container`) with the `no-demo` Spring
      profile active, so `app_user` starts genuinely empty (see Testing architecture note above).
      Covers: registering against the empty database returns `204`; logging in with those
      credentials returns `isSystemAdmin: true` and an empty `tenants` list (spec Acceptance
      Scenario US1/1–2); a second registration in the same test class (database now non-empty)
      returns `isSystemAdmin: false` on login, proving the decision is per-moment, not "anyone who
      registers early" (spec FR-003/FR-005). **Found and fixed a real bug while writing this
      test**: the new endpoint returned `401 Unauthorized` instead of `204` — `AuthController`'s
      `@SecurityRequirement` annotation (Swagger-only, purely documentation) does nothing to
      actually exempt a route from Spring Security's authorization rules. The real access-control
      list lives in `SecurityConfig.java`'s `permitAll()` matcher, which only listed
      `/api/auth/login` — `/api/auth/register` was falling through to the default
      `anyRequest().authenticated()` rule. Fixed by adding `/api/auth/register` to that
      `permitAll()` list. Re-ran: passes.
- [X] T010 [P] [US1] Create `backend/src/test/java/com/tbm/unit/AuthServiceRegisterTest.java`: a
      Mockito-based unit test (no Testcontainers) mocking `AppUserRepository` and
      `PasswordEncoder`, asserting `register()` grants `isSystemAdmin = true` when the repository
      reports no existing accounts and `false` when it reports at least one, and that a duplicate
      username throws `ConflictException` without an `INSERT` occurring. Fast, deterministic
      coverage of the branching logic alongside T009's real-database proof. **Implemented as 3 new
      `@Test` methods added to the existing `backend/src/test/java/com/tbm/unit/AuthServiceTest.java`**
      instead of a new file — that file already exists as the established one-test-class-per-service
      convention (constructing `AuthService` with plain Mockito mocks, matching its existing
      `login()`/`getProfile()` tests exactly), discovered while starting this task; creating a
      second, separate file for the same service would have duplicated that setup for no benefit.
      All 6 tests in the file pass.

**Checkpoint**: User Story 1 is independently verified — an empty platform's very first
registration yields System Admin, proven against both a real empty database and in isolation.

---

## Phase 4: User Story 2 - Self-registration for everyone after the first account (Priority: P1)

**Goal**: Prove that once the platform already has at least one account, every new registration
is always Normal, with no tenant membership and no role choice — and that the full frontend flow
works.

**Independent Test**: Per spec.md — on a platform that already has an account, register through
the page and confirm the result only ever has the simplest role.

### Tests for User Story 2

- [X] T011 [US2] Create `backend/src/test/java/com/tbm/integration/UserSelfRegistrationTest.java`,
      extending `AbstractIntegrationTest` (the always-pre-seeded shared container is exactly the
      right fixture for this story). Covers: registering a new, unused username returns `204` and
      the resulting login shows `isSystemAdmin: false` and `tenants: []` (spec Acceptance Scenario
      US2/1); registering with `ANA_USERNAME` (already seeded) returns `409` (spec FR-002); a blank
      username or password returns `400` (spec FR-008). Depends on T005. **Verified**: all 4 tests
      pass.
- [X] T012 [P] [US2] Create `frontend/tests/unit/CreateUserView.spec.js`, mirroring
      `TenantFormView.spec.js`'s mount/Pinia/`createMemoryHistory` router conventions: submitting
      the form with a valid username/password calls `auth.register` and, on success, navigates to
      `/login`; on failure, the page shows the store's error via `ErrorBanner` (mirroring
      `LoginView.vue`'s own pattern) instead of navigating. Also asserts no role-selection element
      is rendered anywhere in the mounted component (spec FR-003/FR-005). Depends on T007, T008.
      **Verified**: all 4 tests pass.
- [X] T013 [US2] Run the full backend + frontend test suites and confirm zero regressions against
      the T001 baseline. Depends on T011, T012. **Confirmed**: backend and frontend both green,
      zero regressions.

**Checkpoint**: User Story 2 is fully functional and independently testable — registrations
against an already-populated platform always come out Normal, and the end-to-end frontend flow
works.

---

## Phase 5: User Story 3 - Elevated access can never be self-granted after bootstrap (Priority: P1)

**Goal**: Prove the safety guarantee explicitly, under both request tampering and real
concurrency — the two ways this could otherwise fail.

**Independent Test**: Per spec.md — after the platform already has an account, attempt to
tamper the request to claim elevated access and confirm the result is unaffected; separately,
fire two registrations at once against an empty platform and confirm at most one becomes System
Admin.

### Tests for User Story 3

- [X] T014 [US3] Extend `UserSelfRegistrationTest.java` (from T011) with a case that posts a raw
      JSON body including extra fields suggesting elevated access (e.g. `"isSystemAdmin": true`,
      `"role": "ADMIN"`) against the already-non-empty shared container, and asserts the resulting
      account still logs in with `isSystemAdmin: false` — the extra fields have no corresponding
      property on `RegisterRequest` (T002) and are simply dropped during deserialization (spec
      FR-011, Edge Cases). Depends on T011. **Verified**: passes.
- [X] T015 [US3] Extend `UserSelfRegistrationBootstrapTest.java` (from T009) with a concurrency
      case: fire two registration requests at nearly the same instant (e.g., two futures/threads
      hitting the endpoint concurrently) against the freshly-empty database from that test class,
      then assert that logging in as each shows **exactly one** `isSystemAdmin: true` and the
      other `false` — directly exercising research.md §1's advisory lock under real contention
      (spec Edge Cases, US3 Acceptance Scenario 2). Depends on T009. **Found and fixed a real
      concurrency bug this test caught on its first run**: both racers came back `isSystemAdmin:
      true`. Root cause — under PostgreSQL's default READ COMMITTED isolation, a statement's MVCC
      snapshot is taken when the statement *starts* executing, even if it then blocks mid-statement
      waiting on a lock; resuming after the block does **not** refresh the snapshot. T003's original
      implementation combined the lock acquisition and the `EXISTS` check into one statement (a
      `WITH ... SELECT` native query), so the second (blocked) racer's `EXISTS` check resumed using
      the snapshot from *before* it blocked — before the first racer had committed — and still saw
      "no accounts" despite correctly waiting for and acquiring the lock afterward. Confirmed via
      server-side timing instrumentation (added temporarily, then removed) showing the second
      racer's statement genuinely waited ~84ms for the lock, yet still read stale data. Fixed by
      splitting `AppUserRepository.lockAndCheckAnyAccountExists()` into two separate native-query
      methods — `acquireFirstAccountDecisionLock()` and `anyAccountExists()` — called as two
      separate statements from `AuthService.register()`, so the existence check is a genuinely new
      statement that gets a fresh snapshot taken only after the lock is held. Re-verified: 5
      consecutive runs of the concurrency test all pass; `AuthServiceTest.java`'s T010 mocks
      updated to match the new two-method repository API (also caught and fixed a resulting
      compile error). Full backend suite: 112/112 green (102 baseline + 10 new).

**Checkpoint**: All three user stories hold together — bootstrap works, ongoing self-registration
works, and elevated access can never leak through this page after bootstrap, proven under both
tampering and real concurrency.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and end-to-end live-stack confirmation, tying together what the
automated tests already proved in isolation.

- [X] T016 [P] Update `README.md`: add a short section documenting the new account-creation
      entrypoint (mirroring the style of the existing "Seed de demonstração é opcional"
      subsection) — this supersedes feature 005's "no account-creation path outside of demo-data
      seeding" caveat, which is no longer true after this feature. **Done**: added "Criando a
      primeira conta" subsection and fixed the now-false "nenhum login possível" caveat.
- [X] T017 Run `specs/006-user-self-registration/quickstart.md`'s full walkthrough (§1–§5) against
      a real, freshly-emptied `docker compose` stack (`SPRING_PROFILES_ACTIVE=no-demo`) — a final
      human/script-level confirmation against the actual live stack, not just isolated test
      containers, including one more run of the concurrency race from §5. Depends on T013, T014,
      T015. **Verified against the live stack**: §1 first registration → `204`, login shows
      `isSystemAdmin: true`, `tenants: []`; §2 second registration → `204`, login shows
      `isSystemAdmin: false`; §3 duplicate username → `409`; §4 tampered request (extra
      `isSystemAdmin`/`role` fields) → `204` but login still shows `isSystemAdmin: false`; §5
      concurrent race repeated 4 times against fresh volumes — exactly one racer became admin
      every time (the winner varied across rounds, confirming a genuine race, not a fixed
      ordering artifact). Also confirmed the default demo-seeded `docker compose up` path is
      unaffected (`User 3 - ADMIN`/`demo123` still logs in). Additionally did a real-browser
      check (Playwright) of `/criar-conta`: renders correctly, no role picker, submitting
      navigates to `/login` as expected.
- [X] T018 Tear down the stack (`docker compose down`) once T017 is confirmed. Depends on T017.
- [X] T019 Run the full backend + frontend test suites one final time and confirm zero regressions
      against the T001 baseline — the final gate before considering this feature complete. Depends
      on T016, T017, T018. **Confirmed**: backend 112/112 green (102 baseline + 10 new), frontend
      14/14 green (10 baseline + 4 new).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — run first to capture the baseline.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user-story verification work, since
  every story's tests exercise the mechanism this phase builds.
- **User Story 1 (Phase 3)**: Depends on Foundational. This is the MVP.
- **User Story 2 (Phase 4)**: Depends on Foundational. Independent of Phase 3's tests (different
  files, different container strategy) but conventionally follows it since it's next in spec.md's
  priority ordering.
- **User Story 3 (Phase 5)**: Depends on Phase 3 (T009) and Phase 4 (T011) — it extends both of
  their test files rather than creating new ones.
- **Polish (Phase 6)**: Depends on Phases 3–5 — the live-stack walkthrough is a final check after
  every automated guarantee is already proven.

### Within Phase 2 (Foundational)

- T002, T003 are independent of each other (different files).
- T004 depends on T002, T003.
- T005 depends on T004.
- T006 is independent of T002–T005 (different file/layer).
- T007 depends on T006.
- T008 depends on T007.

### Within Phase 3 (User Story 1)

- T009 depends on T005 (needs the real endpoint).
- T010 depends on T004 (tests the service method directly; does not need the HTTP layer) and is
  independent of T009 (different files) — safe to run in parallel with it.

### Within Phase 4 (User Story 2)

- T011 depends on T005.
- T012 depends on T007, T008 — independent of T011 (different files, different layers).
- T013 depends on T011, T012.

### Within Phase 5 (User Story 3)

- T014 depends on T011 (extends that file).
- T015 depends on T009 (extends that file).
- T014 and T015 are independent of each other (different files) — safe to run in parallel.

### Parallel Opportunities

- T002, T003 (Phase 2) can run in parallel — different files.
- T006 (Phase 2) can run in parallel with T002–T005 — different layer entirely.
- T009 and T010 (Phase 3) can run in parallel — different files, different strategies.
- T011 and T012 (Phase 4) can run in parallel — different files.
- T014 and T015 (Phase 5) can run in parallel — different files.
- T016 (Phase 6) can run in parallel with T017's preparation — documentation touches no code.

---

## Parallel Example: Phase 2 → Phase 3

```bash
# Phase 2, in parallel:
Task: "Create RegisterRequest.java DTO"
Task: "Add the advisory-lock-guarded emptiness-check repository method"
Task: "Add the auth store's register() action"

# Phase 3, in parallel (after T004/T005 land):
Task: "Isolated-container bootstrap + first-vs-second-registration integration test"
Task: "Mockito unit test for AuthService.register()'s decision branching"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (baseline) and Phase 2 (the full mechanism, backend + frontend) — required,
   not skippable, since the stories share one implementation surface.
2. Complete Phase 3 (User Story 1) — proves the core bootstrap value on its own.
3. **STOP and VALIDATE**: T009/T010 passing is the MVP gate — an empty platform can be bootstrapped.
4. Phase 4 (ongoing self-registration) and Phase 5 (the safety guarantee) can follow immediately,
   since Phase 2's mechanism already supports them — no rework needed, only new tests.

### Incremental Delivery

1. Setup + Foundational → the mechanism exists, unverified.
2. User Story 1 → bootstrap proven, both against a real empty database and in isolation (MVP).
3. User Story 2 → ongoing self-registration proven, full frontend flow verified.
4. User Story 3 → the safety guarantee proven under tampering and real concurrency.
5. Polish → live-stack confirmation ties everything together against the real stack.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps each task to spec.md's user stories for traceability.
- Commit after each task or logical group.
- Avoid: adding a role field to `RegisterRequest` "just in case" — spec FR-011 is deliberately
  satisfied by that field not existing at all, not by a field that's accepted and ignored
  (research.md §4).

---

## Phase 7: Convergence

- [X] T020 Update `specs/006-user-self-registration/plan.md`'s Project Structure section (backend
      subtree) to match what was actually built: (a) add
      `backend/src/main/java/com/tbm/config/SecurityConfig.java` to the file list, noting the
      `/api/auth/register` addition to the `permitAll()` matcher found and fixed during T009; (b)
      correct `AppUserRepository.java`'s description from "a native-query helper" (singular) to
      the actual two-method split — `acquireFirstAccountDecisionLock()` and `anyAccountExists()`
      — fixed during T015's concurrency-bug remediation; (c) correct the
      `AuthServiceRegisterTest.java` entry — no such file was created; T010 instead added the
      three new tests to the pre-existing `backend/src/test/java/com/tbm/unit/AuthServiceTest.java`
      (plan.md, partial). **Done**: also updated the Summary and Scale/Scope sections with the
      same corrections for full consistency.
- [X] T021 Update the Javadoc on `AuthService.register()` in
      `backend/src/main/java/com/tbm/user/AuthService.java` (around line 56): it links to
      `{@link AppUserRepository#lockAndCheckAnyAccountExists()}`, a method that no longer exists —
      it was split into `acquireFirstAccountDecisionLock()` and `anyAccountExists()` during T015's
      concurrency-bug fix, and `AppUserRepository.java`'s own Javadoc was updated accordingly, but
      this cross-reference in `AuthService.java` was missed. Update it to reference the two actual
      current method names (AuthService.java, contradicts). **Verified**: full backend suite
      112/112 green after the fix.
