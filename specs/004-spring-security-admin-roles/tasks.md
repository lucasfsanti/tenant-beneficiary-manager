---

description: "Task list template for feature implementation"
---

# Tasks: Migrate Admin Role Verification to Spring Security Authorization

**Input**: Design documents from `/specs/004-spring-security-admin-roles/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/README.md, quickstart.md (all present)

**Tests**: This feature's own success criteria (SC-001/SC-004) require zero regressions across the
existing authorization test suite, so test-update and regression-verification tasks are included
throughout — they are framed as *regression gates*, not classic TDD (there is no new user-facing
behavior to write a failing test against; the existing suite is already green and must stay green).

**Organization**: Tasks are grouped by user story per spec.md. Because this feature is an internal
mechanism swap rather than new functionality, User Story 1 (P1) carries essentially all the code
changes; User Story 2 (P2) and User Story 3 (P3) are narrower, independently verifiable slices of
the same migration (see each phase's "Independent Test" below).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every description below includes the exact file path(s) touched

## Path Conventions

Backend-only feature (no frontend changes). All paths are relative to the repository root, under
`backend/src/main/java/com/tbm/` and `backend/src/test/java/com/tbm/`.

---

## Phase 1: Setup

**Purpose**: Establish the pre-migration baseline this feature's zero-regression success criteria
(SC-001/SC-004) are measured against.

- [X] T001 Run the full backend test suite (`cd backend && ./mvnw test`) and confirm it is
      currently all-green before any change — this is the baseline SC-001/SC-004 compare against.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire up the new authorization plumbing (authority population, per-tenant permission
bean, denial-handling, method security) so that Phase 3+ can start declaring `@PreAuthorize` on
individual methods. No `@PreAuthorize` annotation exists on any method yet at the end of this
phase, so no observable behavior changes here — the existing manual checks keep enforcing
everything exactly as before.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Add an `AppUserRepository` dependency to `JwtAuthenticationFilter` and grant a
      `ROLE_SYSTEM_ADMIN` `GrantedAuthority` (in addition to the existing `ROLE_USER`) whenever
      `AppUserRepository.findById(userId).map(AppUser::isSystemAdmin).orElse(false)` is true, in
      `backend/src/main/java/com/tbm/security/JwtAuthenticationFilter.java` (research.md §1).
- [X] T003 [P] Create a new `@Component` `TenantAuthorization` with a method
      `isTenantAdmin(UUID tenantId)` that reads the caller's id from
      `SecurityContextHolder.getContext().getAuthentication()`'s `JwtService.JwtPrincipal` and
      delegates to
      `UserTenantMembershipRepository.existsByUser_IdAndTenant_IdAndIsTenantAdminTrue(userId, tenantId)`,
      in new file `backend/src/main/java/com/tbm/security/TenantAuthorization.java` (research.md §2).
- [X] T004 Update `TenantContextFilter`'s System-Admin bypass condition to read the
      `ROLE_SYSTEM_ADMIN` authority from the current request's `Authentication` (populated by
      T002) instead of its own `AppUserRepository.findById(...).map(AppUser::isSystemAdmin)`
      lookup; leave the membership-existence rejection branch untouched, in
      `backend/src/main/java/com/tbm/security/TenantContextFilter.java` (research.md §5). Depends
      on T002.
- [X] T005 In `backend/src/main/java/com/tbm/config/SecurityConfig.java`: add
      `@EnableMethodSecurity` to the class; update the `JwtAuthenticationFilter` and
      `TenantContextFilter` instantiations in `securityFilterChain(...)` to match their new
      constructor signatures (T002/T004). Depends on T002, T004. **Design correction discovered
      during implementation**: the original plan for this task also registered a filter-level
      `AccessDeniedHandler` bean here, writing the `403` `ProblemDetail` directly. Once
      implemented, every denial-path test failed with `500` instead of `403` — `ApiExceptionHandler`'s
      existing `@ExceptionHandler(Exception.class)` catch-all intercepts `AccessDeniedException`
      at the MVC layer before it can ever reach a filter-level handler (every `@PreAuthorize` in
      this app guards a method called during MVC dispatch). The `AccessDeniedHandler` bean was
      removed from this file; the fix instead lives in T012 below. See research.md §4 for the full
      account.

**Checkpoint**: Foundation ready — `ROLE_SYSTEM_ADMIN` is populated per request, the
`TenantAuthorization` bean is available for SpEL, method security is enabled, and denials will be
handled correctly once any method declares `@PreAuthorize`. Nothing is annotated yet.

---

## Phase 3: User Story 1 - Centralized enforcement of admin-only operations (Priority: P1) 🎯 MVP

**Goal**: Replace all 12 manual admin-standing checks (in `TenantService`, `MembershipService`,
`AppUserService`) with `@PreAuthorize`, per the mapping in data-model.md's Protected Operations
table, with zero change in who is authorized to do what (FR-007).

**Independent Test**: A user without the required standing is blocked from every currently
admin-protected operation, and the protection is driven by the `@PreAuthorize` declaration on that
operation rather than a parallel manual check — confirmed by the full existing authorization
regression suite passing unmodified in its assertions.

### Implementation for User Story 1

- [X] T006 [P] [US1] In `backend/src/main/java/com/tbm/tenant/TenantService.java`: add
      `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` to `list`, `create`, `delete`, and
      `@PreAuthorize("hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)")`
      to `get`, `update`; remove the `requireSystemAdmin`, `requireSystemAdminOrTenantAdmin`, and
      `isSystemAdmin` private helper methods; remove the now-unused `callerId` parameter from all
      five public methods (data-model.md rows 1–5, research.md §3/§6). Reuse the shared
      `@tenantAuthorization` bean as-is — do not add a method-specific query (spec SC-003).
      Confirm `list`/`create`/`get`/`update`/`delete` are this class's only public methods, so
      spec FR-010 (no bleed-over to unprotected siblings) has nothing else to touch.
- [X] T007 [US1] Update `backend/src/main/java/com/tbm/tenant/TenantController.java` to stop
      passing `principal.userId()` into `list`/`create`/`get`/`update`/`delete`, matching T006's
      reduced method signatures. Depends on T006.
- [X] T008 [P] [US1] In `backend/src/main/java/com/tbm/tenant/MembershipService.java`: add
      `@PreAuthorize("hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)")`
      to `listMembers`, `addMember`, `removeMember`, `grantTenantAdmin`, `revokeTenantAdmin`;
      remove the `requireStandingFor` private helper; remove the now-unused `callerId` parameter
      from all five public methods (data-model.md rows 6–10, research.md §3/§6). Reuse the shared
      `@tenantAuthorization` bean as-is — do not add a method-specific query (spec SC-003).
      Confirm these five methods are this class's only public methods, so spec FR-010 (no
      bleed-over to unprotected siblings) has nothing else to touch.
- [X] T009 [US1] Update `backend/src/main/java/com/tbm/tenant/MembershipController.java` to stop
      passing `principal.userId()` into `list`/`add`/`remove`/`grantTenantAdmin`/`revokeTenantAdmin`,
      matching T008's reduced method signatures. Depends on T008.
- [X] T010 [P] [US1] In `backend/src/main/java/com/tbm/user/AppUserService.java`: add
      `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` to `grantSystemAdmin` and `revokeSystemAdmin`;
      remove the `requireSystemAdmin` private helper; remove the now-unused `callerId` parameter
      from both methods; leave `revokeSystemAdmin`'s last-admin pessimistic-lock logic
      (`findAllSystemAdminsForUpdate`) completely untouched (data-model.md rows 11–12, research.md
      §3/§6/§7, spec FR-006). No method-specific query is introduced (spec SC-003). Confirm
      `grantSystemAdmin`/`revokeSystemAdmin` are this class's only public methods, so spec FR-010
      (no bleed-over to unprotected siblings) has nothing else to touch.
- [X] T011 [US1] Update `backend/src/main/java/com/tbm/user/UserAdminController.java` to stop
      passing `principal.userId()` into `grant`/`revoke`, matching T010's reduced method
      signatures. Depends on T010.
- [X] T012 [US1] Delete `backend/src/main/java/com/tbm/common/exception/ForbiddenException.java`
      and remove its `handleForbidden` mapping from
      `backend/src/main/java/com/tbm/common/ApiExceptionHandler.java` — confirmed to have no
      remaining callers once T006/T008/T010 land (research.md §4). Depends on T005, T006, T008,
      T010. **Expanded scope (see T005's correction note)**: also added
      `handleAccessDenied(AccessDeniedException)` to `ApiExceptionHandler`, producing the same
      `403` shape `handleForbidden` used to — this is where the `AccessDeniedException` → `403`
      mapping actually lives now, since it must sit inside this class to win over its own
      `Exception.class` catch-all (research.md §4).

### Regression Verification for User Story 1

- [X] T013 [US1] Update call sites for the removed `callerId` parameter (per T006/T008/T010) in
      the existing integration suite, without changing any test's assertions:
      `backend/src/test/java/com/tbm/integration/TenantCrudTest.java`,
      `SystemAdminGrantRevokeTest.java`, `SystemAdminConcurrentRevokeTest.java`,
      `TenantAdminGrantRevokeTest.java`, `TenantUpdateAuthorizationTest.java`,
      `NormalUserRoleBaselineTest.java`, `SystemAdminBeneficiarioAccessTest.java`,
      `MembershipManagementTest.java`, `TenantMembershipEnforcementTest.java`. Depends on T006,
      T008, T010. **Verified no-op**: a clean `mvn test-compile` after T006/T008/T010 showed zero
      errors in any of these 9 classes — they drive the app via MockMvc/HTTP, never call the
      service methods directly, so they never referenced `callerId` to begin with.
- [X] T014 [US1] Update
      `backend/src/test/java/com/tbm/security/TenantContextFilterTest.java` to grant
      `ROLE_SYSTEM_ADMIN` via a test `Authentication`/`GrantedAuthority` instead of stubbing
      `AppUserRepository` directly, matching T004's new bypass logic. Depends on T004. **Scope
      correction**: this test's one scenario (`passesThroughWhenThePrincipalIsNotAJwtPrincipal`)
      doesn't exercise the System-Admin-bypass branch at all — only removed the now-invalid
      `mock(AppUserRepository.class)` constructor argument to match the new 2-arg constructor. No
      authority-granting test was needed here; System-Admin-bypass behavior is covered by
      `SystemAdminBeneficiarioAccessTest` (integration).
- [X] T015 [US1] Remove the now-obsolete `ForbiddenException` test case from
      `backend/src/test/java/com/tbm/unit/ApiExceptionHandlerTest.java`. Depends on T012.
      **Verified no-op**: this file only ever tested `handleUnexpected` (the 500 fallback); it
      never had a `ForbiddenException` case to remove.
- [X] T016 [US1] Run the full backend test suite (`cd backend && ./mvnw test`) and confirm zero
      regressions against the T001 baseline (SC-001). Depends on T013, T014, T015. First run
      surfaced 8 real failures (every denial-path test got `500` instead of `403`) — root-caused
      to the `AccessDeniedHandler` design flaw described in T005/T012's correction notes. After
      that fix, a clean `mvn test` run passed 100/100, matching the T001 baseline exactly.

**Checkpoint**: At this point, User Story 1 is fully functional and independently testable — every
admin-gated operation is enforced by `@PreAuthorize`, and the full regression suite is green. (This
checkpoint is the validation milestone for the *story*, not a strict start-gate for Phase 4: per
"Parallel Opportunities" below, T017 can start as soon as T006/T008/T010 land, before every task in
this phase finishes.)

---

## Phase 4: User Story 2 - Auditable, single-source access-control coverage (Priority: P2)

**Goal**: Make the required standing for every protected operation mechanically verifiable from
its declaration, not just visually inspectable (spec FR-008/SC-002).

**Independent Test**: A dedicated test enumerates the same 12 methods as data-model.md's Protected
Operations table and fails if any method's `@PreAuthorize` expression is missing or diverges from
the table — proof that "discoverable from one declaration" is a checked property, not just a
documentation claim.

- [X] T017 [P] [US2] Add a new reflection-based unit test,
      `backend/src/test/java/com/tbm/unit/AuthorizationDeclarationCoverageTest.java`, that asserts
      each of the 12 methods listed in data-model.md's Protected Operations table carries the
      exact `@PreAuthorize` expression shown in that table's "New mechanism" column. Depends on
      T006, T008, T010.
- [X] T018 [US2] Cross-check `specs/004-spring-security-admin-roles/data-model.md`'s Protected
      Operations table against the final `@PreAuthorize` expressions once implementation is
      complete, and correct the table if anything drifted during implementation. Depends on T017.
      **No drift found**: the table's "New mechanism" column matches every implemented annotation
      exactly (confirmed both by manual comparison and by T017's passing coverage test, which
      asserts against the same expressions); no edit to data-model.md was needed.

**Checkpoint**: User Stories 1 and 2 both hold — every operation is annotated, and a test proves
the annotations match the documented mapping.

---

## Phase 5: User Story 3 - Last-System-Admin safeguard keeps working (Priority: P3)

**Goal**: Confirm the pre-existing last-System-Admin invariant is unaffected by the authorization
mechanism change (spec FR-006) — this story requires no code change, only verification.

**Independent Test**: Attempting to revoke System Admin standing from the sole remaining System
Admin is still rejected, exactly as before the migration.

- [X] T019 [US3] Run the last-admin-focused tests specifically
      (`cd backend && ./mvnw test -Dtest=SystemAdminGrantRevokeTest,SystemAdminConcurrentRevokeTest`)
      and confirm the safeguard still rejects removal of the platform's final System Admin,
      unaffected by T006–T012 (SC-004). Depends on T013.

**Checkpoint**: All three user stories are independently verified; the migration is complete.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final end-to-end sanity check beyond the automated suite.

- [X] T020 [P] Execute quickstart.md's manual validation steps 3–5 (revocation-freshness check,
      Tenant Admin scoping check, last-System-Admin safeguard check) against a running
      `docker-compose up` stack. Depends on T016, T019. **All three confirmed** against the live
      stack (`docker compose up -d --build`) using seed users `admin`/`ana`/`bruno`: (1) granting
      then revoking `ana`'s System Admin standing mid-session blocked her very next request with
      her original, still-unexpired token — `403` with the exact RFC 7807 body
      (`title: "Acesso negado"`); (2) `bruno` (Tenant Admin of Tenant Alfa only) could update
      Alfa (`200`) but not Tenant Beta (`403`, same body shape); (3) the platform's sole System
      Admin attempting to revoke their own standing got `400` with the last-admin business-rule
      message, unchanged from before the migration.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — run first to capture the baseline.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories (T002–T005 must land
  before any `@PreAuthorize` annotation is added).
- **User Story 1 (Phase 3)**: Depends on Foundational. This is the MVP — it contains essentially
  all the code changes.
- **User Story 2 (Phase 4)**: Depends on User Story 1's annotations existing (T006/T008/T010) —
  it verifies them, so it cannot start before they land, but it does not change them.
- **User Story 3 (Phase 5)**: Depends on User Story 1's test-suite updates (T013) to run against —
  it is pure verification, no new code.
- **Polish (Phase 6)**: Depends on Phases 3 and 5 being complete.

### Within Phase 3 (User Story 1)

- T006, T008, T010 are independent of each other (different files) — can run in parallel.
- T007 depends on T006; T009 depends on T008; T011 depends on T010 (each controller depends only
  on its own service).
- T012 depends on all three services being migrated (T006, T008, T010) plus T005 (the new denial
  handler must exist before the old one is removed).
- T013–T015 (test updates) depend on the corresponding implementation tasks; T016 (full suite run)
  is the final gate for the phase.

### Parallel Opportunities

- T002 and T003 (Phase 2) can run in parallel — different files, no shared dependency.
- T006, T008, T010 (Phase 3) can run in parallel — three different service classes.
- T017 (Phase 4) can start as soon as T006/T008/T010 land, in parallel with T007/T009/T011/T012
  and the Phase 3 test-update tasks, since it only reads the annotations, not the controllers.
- T020 (Phase 6) has no same-file conflicts with anything else still open at that point.

---

## Parallel Example: Phase 2 → Phase 3 handoff

```bash
# Phase 2, in parallel:
Task: "Add AppUserRepository to JwtAuthenticationFilter, grant ROLE_SYSTEM_ADMIN"
Task: "Create TenantAuthorization @Component with isTenantAdmin(UUID tenantId)"

# Then, once Phase 2 is fully checkpointed, Phase 3 in parallel:
Task: "Add @PreAuthorize to TenantService's 5 methods, drop callerId"
Task: "Add @PreAuthorize to MembershipService's 5 methods, drop callerId"
Task: "Add @PreAuthorize to AppUserService's 2 methods, drop callerId"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (baseline) and Phase 2 (foundational plumbing) — required, not skippable.
2. Complete Phase 3 (User Story 1) — this alone delivers the entire migration's behavior change.
3. **STOP and VALIDATE**: T016's full suite run is the MVP gate — zero regressions, full
   annotation coverage.
4. Phases 4 and 5 add verification/audit value on top of an already-complete migration; they can
   be done immediately after or deferred slightly without leaving the system in a half-migrated
   state, since User Story 1 alone is the complete, atomic change (per spec.md's Assumptions).

### Incremental Delivery

1. Setup + Foundational → plumbing ready, no behavior change yet.
2. User Story 1 → the migration itself, fully regression-tested (MVP).
3. User Story 2 → adds a standing test-suite guard against future annotation drift.
4. User Story 3 → confirms the one safeguard this migration must not disturb.
5. Polish → manual end-to-end confirmation via quickstart.md.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps each task to spec.md's user stories for traceability.
- This feature has no frontend tasks — see plan.md's Project Structure ("frontend/ — untouched").
- Commit after each task or logical group.
- Avoid: touching `SecurityConfig.java` in more than one task at a time (T005 owns all of its
  edits); touching a service class in more than one task at a time (T006/T008/T010 each own their
  whole file).
