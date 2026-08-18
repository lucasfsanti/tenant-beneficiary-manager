---

description: "Task list for Role-Based Access for Users"
---

# Tasks: Role-Based Access for Users

**Input**: Design documents from `/specs/003-rbac-user-roles/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md (all present)

**Tests**: Included. This feature adds privilege-escalation-sensitive authorization logic (last-System-Admin protection, cross-tenant boundaries, self-targeting rules — validated via the `authorization.md` checklist). Following the precedent set by `001-pessoa-beneficiario-crud` (which treats Testcontainers-backed integration tests, including a dedicated isolation suite, as required deliverables rather than optional extras), tests here are required, not optional.

**Organization**: Tasks are grouped by user story (spec.md priorities P1–P3) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US3)
- File paths are relative to the repository root

## Path Conventions

Web app per plan.md: `backend/src/main/java/com/tbm/...` (Spring Boot), `backend/src/test/java/com/tbm/...`, `frontend/src/...` (Vue 3), `frontend/tests/...`.

## Naming Conventions

- New backend code extends the existing `tenant` and `user` packages (not a new `rbac` package) — see plan.md's Structure Decision.
- `Tenant`'s JSON field is `name` (not `nome`), matching the existing `TenantSummary` DTO convention already established in `001`.
- All new UI-facing text (labels, buttons, messages) MUST be in Brazilian Portuguese, per the platform-wide convention established in `001` (FR-025) — flagged on frontend tasks below.
- FR-005(c)/FR-014's self-targeting and idempotency rules, and FR-011's atomicity requirement, MUST be reflected in the corresponding service methods and their tests — flagged below.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Schema migration and the new 403 error path

- [X] T001 Create Liquibase changeset `backend/src/main/resources/db/changelog/003-role-system.sql`: add `is_system_admin BOOLEAN NOT NULL DEFAULT false` to `app_user`; add `is_tenant_admin BOOLEAN NOT NULL DEFAULT false` to `user_tenant_membership`; seed a new `admin`/`demo123` user with `is_system_admin = true` and no memberships; set `bruno`'s existing Tenant Alfa membership to `is_tenant_admin = true` — per data-model.md and research.md §7
- [X] T002 Register `003-role-system.sql` as a new `include` entry in `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (depends on T001)
- [X] T003 [P] Create `ForbiddenException` in `backend/src/main/java/com/tbm/common/exception/ForbiddenException.java`, matching the existing `NotFoundException`/`ConflictException` style
- [X] T004 Add a `ForbiddenException` → 403 `@ExceptionHandler` (title "Acesso negado") in `backend/src/main/java/com/tbm/common/ApiExceptionHandler.java` — per research.md §4 (depends on T003)

**Checkpoint**: Schema and error-handling groundwork ready.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entity/repository/DTO changes and the shared user-lookup endpoint that every user story depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] Add `isSystemAdmin` field (+ getter/setter) to `AppUser` in `backend/src/main/java/com/tbm/user/AppUser.java` (depends on T002)
- [X] T006 [P] Add `isTenantAdmin` field (+ getter/setter) to `UserTenantMembership` in `backend/src/main/java/com/tbm/user/UserTenantMembership.java` (depends on T002)
- [X] T007 Add `findAllSystemAdminsForUpdate()` (`@Lock(LockModeType.PESSIMISTIC_WRITE)` over `WHERE isSystemAdmin = true`) to `AppUserRepository` in `backend/src/main/java/com/tbm/user/AppUserRepository.java` — per research.md §9's atomicity design for FR-011 (locks the whole admin row set, not just the target row) (depends on T005)
- [X] T008 [P] Add `existsByUser_IdAndTenant_IdAndIsTenantAdminTrue`, `existsByTenant_Id`, and `findByUser_IdAndTenant_Id` to `UserTenantMembershipRepository` in `backend/src/main/java/com/tbm/user/UserTenantMembershipRepository.java` (depends on T006)
- [X] T009 [P] Add `existsByTenantId(UUID tenantId)` to `BeneficiarioRepository` in `backend/src/main/java/com/tbm/beneficiario/BeneficiarioRepository.java` — per research.md §8, used by the Tenant-deletion referential-safety check
- [X] T010 Extend `UserProfile` with `isSystemAdmin` in `backend/src/main/java/com/tbm/user/dto/UserProfile.java`; extend `TenantSummary` with `isTenantAdmin` in `backend/src/main/java/com/tbm/user/dto/TenantSummary.java` (depends on T005, T006)
- [X] T011 Update `AuthService.buildProfile()` in `backend/src/main/java/com/tbm/user/AuthService.java` to populate the new `isSystemAdmin`/`isTenantAdmin` fields (depends on T007, T008, T010)
- [X] T012 [P] Add `UserSummary` DTO (`id`, `username`) in `backend/src/main/java/com/tbm/user/dto/UserSummary.java`
- [X] T013 Implement `GET /api/users?username=` (exact-match lookup, any authenticated caller — research.md §6) via a new `UserController` in `backend/src/main/java/com/tbm/user/UserController.java`, adding a matching `findByUsername`-based query to `AppUserRepository` (FR-016) (depends on T012)
- [X] T014 [P] Add `ADMIN_USERNAME`/`ADMIN_PASSWORD` and `BRUNO_USERNAME`/`BRUNO_PASSWORD` constants (matching the existing `ANA_USERNAME` pattern) to `backend/src/test/java/com/tbm/integration/AbstractIntegrationTest.java` (depends on T002)
- [X] T015 [P] Add `isSystemAdmin` and `isTenantAdminFor(tenantId)` getters to the Pinia auth store in `frontend/src/stores/auth.js`, reading from the now-extended `user`/`user.tenants` shape (depends on T010, T011)

**Checkpoint**: Foundation ready — schema, entities, repositories, extended profile, and the shared user-lookup endpoint all work; user story implementation can now begin.

---

## Phase 3: User Story 1 - System Admin manages the platform's tenants and admins (Priority: P1) 🎯 MVP

**Goal**: A System Admin can perform full Tenant CRUD and grant/revoke System Admin standing (including their own, subject to the last-admin protection); no one else can.

**Independent Test**: Signed in as a System Admin, create/edit/delete a Tenant and grant/revoke another user's System Admin standing — all without needing any other role; confirm a non-System-Admin is denied every one of these actions.

### Tests for User Story 1 ⚠️

> Write these tests FIRST, ensure they FAIL before implementation

- [X] T016 [P] [US1] Integration test: as System Admin, `POST`, `GET /api/tenants` (list, 200), `GET /api/tenants/{id}` (single, 200), `PUT`, and `DELETE` all succeed; as a user with no standing for that tenant, all five actions return 403; as a Tenant Admin of that tenant, `POST` (create), `GET /api/tenants` (list-all), and `DELETE` also return 403 — the Tenant Admin's allowed `GET /api/tenants/{id}`/`PUT` path is covered separately by T034, not here. (SC-001's "immediately assignable to memberships" is covered by T032, once the membership endpoint exists — not here, to avoid a forward reference to a Phase 4 endpoint.) — in `backend/src/test/java/com/tbm/integration/TenantCrudTest.java` (depends on T014)
- [X] T017 [P] [US1] Integration test: Tenant deletion is blocked (400) while any Beneficiário record or membership references it, with a generic explanation — in `backend/src/test/java/com/tbm/integration/TenantDeletionRestrictionTest.java` (depends on T014)
- [X] T018 [P] [US1] Integration test: System Admin grant/revoke — grant to another user succeeds and is visible on that user's very next `/api/me` call (FR-015); revoke succeeds while >1 System Admin exists, including self-revoke, and when admin A revokes admin B's standing, B's own very next request already reflects the loss (SC-010); revoke of the last remaining System Admin (including by themselves) returns 400; re-granting already-held or re-revoking not-held standing is a no-op 204; grant/revoke against a nonexistent `userId` returns 404; a non-System-Admin caller gets 403 — in `backend/src/test/java/com/tbm/integration/SystemAdminGrantRevokeTest.java` (depends on T014)
- [X] T019 [P] [US1] Integration test: two concurrent revoke requests issued from parallel threads against the platform's last two System Admins — exactly one succeeds, the count never reaches zero (FR-011 atomicity) — in `backend/src/test/java/com/tbm/integration/SystemAdminConcurrentRevokeTest.java` (depends on T014)

### Implementation for User Story 1

- [X] T020 [P] [US1] Add `TenantInput` DTO (`{ name }`) in `backend/src/main/java/com/tbm/tenant/dto/TenantInput.java`
- [X] T021 [P] [US1] Add `TenantResponse` DTO (`{ id, name }`) in `backend/src/main/java/com/tbm/tenant/dto/TenantResponse.java`
- [X] T022 [US1] Implement `TenantService` — create/delete restricted to `isSystemAdmin`; list (`GET /api/tenants`) restricted to `isSystemAdmin`; get-single and update (`GET`/`PUT /api/tenants/{id}`) allowed for `isSystemAdmin` OR `isTenantAdmin` on that tenant (FR-004/FR-005(b)); delete blocked (400) via T009's/`UserTenantMembershipRepository.existsByTenant_Id` existence checks (FR-003) — in `backend/src/main/java/com/tbm/tenant/TenantService.java` (depends on T020, T021, T007, T008, T009)
- [X] T023 [US1] Implement `TenantController` (`GET/POST /api/tenants`, `GET/PUT/DELETE /api/tenants/{tenantId}`) in `backend/src/main/java/com/tbm/tenant/TenantController.java` (depends on T022)
- [X] T024 [US1] Implement System Admin grant/revoke in `AppUserService` — self-targeting allowed, idempotent no-op, not-found for invalid `userId`, atomic last-admin protection via T007's locking read (FR-011/FR-014) — in `backend/src/main/java/com/tbm/user/AppUserService.java` (depends on T007)
- [X] T025 [US1] Implement `UserAdminController` (`PUT/DELETE /api/users/{userId}/system-admin`) in `backend/src/main/java/com/tbm/user/UserAdminController.java` (depends on T024)
- [X] T026 [P] [US1] Implement `frontend/src/services/tenantAdminApi.js` with tenant CRUD and system-admin grant/revoke calls
- [X] T027 [US1] Implement Pinia store `frontend/src/stores/tenant.js` (tenant list/create/update/delete, system-admin grant/revoke actions) (depends on T026)
- [X] T028 [US1] Implement `frontend/src/views/TenantListView.vue` (System Admin: list/create/delete tenants; Portuguese labels) (depends on T027)
- [X] T029 [US1] Implement `frontend/src/views/TenantFormView.vue` (create/edit tenant name; Portuguese labels) (depends on T027)
- [X] T030 [US1] Implement `frontend/src/views/SystemAdminsView.vue` (user lookup via T013, list/grant/revoke System Admin standing; Portuguese labels) (depends on T027, T013)
- [X] T031 [US1] Add role-gated routes (`/tenants`, `/tenants/novo`, `/tenants/:id/editar`, `/admins`) with a `requiresSystemAdmin` meta guard checked against `auth.isSystemAdmin` in `frontend/src/router/index.js` (depends on T028, T029, T030, T015)

**Checkpoint**: User Story 1 is fully functional and independently testable/demoable — this is the MVP.

---

## Phase 4: User Story 2 - Tenant Admin manages their own tenant's membership and details (Priority: P2)

**Goal**: A Tenant Admin can add/remove members of their own tenant, edit that tenant's own attributes, and grant/revoke Tenant Admin standing (including their own) for members of that same tenant — none of it crossing into any other tenant.

**Independent Test**: Signed in as a Tenant Admin of Tenant A (and nothing more than a Normal user of Tenant B), add/remove a member, edit Tenant A's name, and grant/revoke another member's Tenant Admin standing — confirm every attempt against Tenant B is denied.

### Tests for User Story 2 ⚠️

- [X] T032 [P] [US2] Integration test: add/remove membership as Tenant Admin of that tenant succeeds, including immediately after that Tenant's own creation by a System Admin (SC-001); the member list reflects it; the same actions against a tenant the caller doesn't administer return 403; adding a membership for a nonexistent `userId` returns 404 — in `backend/src/test/java/com/tbm/integration/MembershipManagementTest.java` (depends on T014, T023)
- [X] T033 [P] [US2] Integration test: Tenant Admin grant/revoke of Tenant Admin standing — grant to an existing member succeeds and is visible on that member's very next request (FR-015); grant to a non-member returns 404 without creating membership; self-revoke succeeds with no last-admin protection; when the granter revokes a *different* member's standing, that member's own very next request already reflects the loss (SC-010); grant/revoke against another tenant returns 403; already-held/not-held is a no-op 204 — in `backend/src/test/java/com/tbm/integration/TenantAdminGrantRevokeTest.java` (depends on T014)
- [X] T034 [P] [US2] Integration test: a Tenant Admin can update their own tenant's name (200); a System Admin can too; a Normal-tier member or a caller with no standing for that tenant gets 403 — in `backend/src/test/java/com/tbm/integration/TenantUpdateAuthorizationTest.java` (depends on T014, T023)

### Implementation for User Story 2

- [X] T035 [P] [US2] Add `MemberResponse` DTO (`userId`, `username`, `isTenantAdmin`) in `backend/src/main/java/com/tbm/tenant/dto/MemberResponse.java`
- [X] T036 [P] [US2] Add `AddMemberRequest` DTO (`{ userId }`) in `backend/src/main/java/com/tbm/tenant/dto/AddMemberRequest.java`
- [X] T037 [US2] Implement `MembershipService` — add/remove membership and grant/revoke Tenant Admin standing, authorized for `isSystemAdmin` OR `isTenantAdmin` on that specific tenant (FR-005/FR-006), self-targeting allowed and idempotent (FR-005(c)), non-member grant target returns 404, add-membership target validated via `AppUserRepository` (404 if the `userId` doesn't exist) — in `backend/src/main/java/com/tbm/tenant/MembershipService.java` (depends on T035, T036, T008)
- [X] T038 [US2] Implement `MembershipController` (`GET/POST /api/tenants/{tenantId}/members`, `DELETE /api/tenants/{tenantId}/members/{userId}`, `PUT/DELETE /api/tenants/{tenantId}/members/{userId}/tenant-admin`) in `backend/src/main/java/com/tbm/tenant/MembershipController.java` (depends on T037)
- [X] T039 [US2] Extend `frontend/src/services/tenantAdminApi.js` with member list/add/remove and tenant-admin grant/revoke calls (depends on T026)
- [X] T040 [US2] Extend `frontend/src/stores/tenant.js` with member list/add/remove and tenant-admin grant/revoke actions (depends on T027, T039)
- [X] T041 [US2] Add a member-management panel (list, add via T013's user lookup, remove, grant/revoke Tenant Admin) to `frontend/src/views/TenantFormView.vue`; Portuguese labels (depends on T029, T040)
- [X] T042 [US2] Extend the `frontend/src/router/index.js` tenant-edit route guard and `TenantFormView.vue` to also allow a Tenant Admin of that specific tenant (not just System Admin) (depends on T031, T041)

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Normal user keeps working exactly as today (Priority: P3)

**Goal**: Confirm role tier is purely additive — a user with no elevated standing sees zero change to existing Pessoa/Beneficiário behavior, and is denied every new Tenant/membership-management action.

**Independent Test**: Signed in as a user with no elevated standing, perform the same Pessoa/Beneficiário operations available before this feature and confirm nothing changed; confirm every new Tenant/membership endpoint returns 403.

### Tests for User Story 3 ⚠️

- [X] T043 [US3] Integration test: a Normal-tier user's existing Pessoa and Beneficiário CRUD (from `001`) succeeds unchanged; the same user's attempts at Tenant create/update/delete and any membership/tenant-admin-grant endpoint all return 403 (FR-007/FR-010) — in `backend/src/test/java/com/tbm/integration/NormalUserRoleBaselineTest.java` (depends on T023, T038)

### Polish for User Story 3

- [X] T044 [US3] Run `cd backend && mvn test` and confirm the full pre-existing `001`/`002` integration suite still passes unchanged alongside the new tests (SC-005) (depends on T019, T033, T034, T043)

**Checkpoint**: All three user stories are independently functional — the full feature is complete.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Delivery requirements that span all user stories

- [X] T045 [P] Verify the generated Swagger UI/OpenAPI output matches `specs/003-rbac-user-roles/contracts/openapi.yaml`; adjust controller annotations as needed
- [X] T046 [P] Add a short "Controle de acesso por papéis" subsection to `README.md`'s architecture section describing the three tiers and where each authorization check is enforced (per plan.md's Delivery & Documentation Requirements note)
- [X] T047 Run `docker-compose up` from a clean state and execute every step of `specs/003-rbac-user-roles/quickstart.md` end-to-end (depends on all prior phases)
- [X] T048 [P] Frontend unit test: `TenantListView` is reachable/rendered only when `auth.isSystemAdmin` is true — in `frontend/tests/unit/TenantListView.spec.js`
- [X] T049 [P] Frontend unit test: `TenantFormView`'s member-management actions (add/remove/grant/revoke) call the corresponding `tenant` store actions — in `frontend/tests/unit/TenantFormView.spec.js`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately. T003/T004 (exception) can run in parallel with T001/T002 (schema).
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories (schema, entities, DTOs, and the shared user-lookup endpoint are needed by every story).
- **User Story 1 (Phase 3)**: Depends on Foundational completion. Tests (T016–T019) can all run in parallel once T014 exists; implementation (T020–T031) follows the DTO → service → controller → frontend chain described inline.
- **User Story 2 (Phase 4)**: Depends on Foundational completion, and on T023 (Tenant update endpoint, for T034) and T026 (shared API service file, for T039). Otherwise independent of US1's grant/revoke and Tenant-list work.
- **User Story 3 (Phase 5)**: Depends on Foundational completion plus T023/T038 (needs the actual endpoints to exist to assert they deny a Normal-tier caller).
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- DTOs before services before controllers
- Backend before the frontend pieces that consume it
- Story complete and checkpointed before moving to the next priority

### Parallel Opportunities

- T001/T002 (schema) and T003/T004 (exception) — Setup
- T005, T006, T008, T009, T012, T014, T015 — Foundational (different files, independent once their own single-line dependency is met)
- T016–T019 (all US1 tests) — parallel once T014 exists
- T020/T021 (US1 DTOs) — parallel
- T026 (US1 API service) can start as soon as T023/T025 exist; is itself a dependency of both T027 (US1) and T039 (US2)
- T032–T034 (all US2 tests) — parallel once T014 (and T023 for T034) exist
- T035/T036 (US2 DTOs) — parallel
- T045, T046, T048, T049 — Polish

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Integration test: Tenant CRUD in backend/src/test/java/com/tbm/integration/TenantCrudTest.java"
Task: "Integration test: Tenant deletion restriction in backend/src/test/java/com/tbm/integration/TenantDeletionRestrictionTest.java"
Task: "Integration test: System Admin grant/revoke in backend/src/test/java/com/tbm/integration/SystemAdminGrantRevokeTest.java"
Task: "Integration test: concurrent System Admin revoke in backend/src/test/java/com/tbm/integration/SystemAdminConcurrentRevokeTest.java"

# Launch independent DTOs together:
Task: "Add TenantInput DTO in backend/src/main/java/com/tbm/tenant/dto/TenantInput.java"
Task: "Add TenantResponse DTO in backend/src/main/java/com/tbm/tenant/dto/TenantResponse.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (Tenant CRUD + System Admin grant/revoke)
4. **STOP and VALIDATE**: Run T016–T019 and confirm Tenant management and System Admin grant/revoke work standalone, including the atomicity/last-admin edge cases
5. Demo if ready — full Tenant lifecycle management, previously nonexistent, is a legitimate MVP slice

### Incremental Delivery

1. Setup + Foundational → schema, entities, shared lookup endpoint ready
2. Add US1 (System Admin) → validate independently → demo (MVP)
3. Add US2 (Tenant Admin) → validate independently → demo (delegated, per-tenant administration)
4. Add US3 (Normal user regression) → validate independently → demo (confirms zero regressions)
5. Phase 6 polish (README, OpenAPI check, full quickstart run, frontend unit tests) closes out delivery requirements

### Suggested Task Ownership Split (if staffed by more than one person)

1. Everyone completes Setup + Foundational together (it blocks everything)
2. Developer A: US1 backend (Tenant CRUD + System Admin grant/revoke) + frontend
3. Developer B: US2 backend (membership + Tenant Admin grant/revoke, after T023/T026 land) + frontend
4. Either developer picks up US3 (pure verification) once both endpoint sets exist
5. Either developer closes out Phase 6 polish

---

## Notes

- [P] tasks = different files, no unmet dependencies
- [Story] label maps each task to its user story for traceability
- Every user story is independently completable and testable per its Independent Test criterion in spec.md
- Verify tests fail before implementing (Tests sections are listed first in every story phase)
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently before moving on
- FR-011's atomicity requirement (T007, T019) and FR-005(c)/FR-014's self-targeting + idempotency rules (T022/T024/T037) come directly from the `authorization.md` checklist resolution — do not simplify them away during implementation
- Terminology: "grant"/"revoke" are the canonical verbs throughout (per spec.md's clarify-round terminology normalization) — use them in code/commit messages, not "promote"/"demote"

---

## Phase 7: Convergence

**Purpose**: Close a gap between spec.md and the implementation, found by `/speckit-converge` after `/speckit-implement` completed T001–T049: System Admin standing was never wired into the Beneficiário access path, only into Tenant/membership management.

- [X] T050 Update `TenantContextFilter` (`backend/src/main/java/com/tbm/security/TenantContextFilter.java`) to let a caller holding System Admin standing pass the `X-Tenant-Id` check for any tenant, not only tenants where they hold a `UserTenantMembership` row; add a regression test confirming a System Admin can list/get/create/update/delete Beneficiário records in a tenant they have no membership in per FR-008 (missing)
- [X] T051 Update `TenantSwitcher.vue` (`frontend/src/components/TenantSwitcher.vue`) and/or `frontend/src/stores/auth.js` so a signed-in System Admin can select any tenant — not only their own memberships, which are empty for a memberless System Admin like the seeded `admin` account — to browse Beneficiário data for, per FR-008 (missing) (depends on T050)
