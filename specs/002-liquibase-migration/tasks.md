---

description: "Task list for migrating database migrations to Liquibase"
---

# Tasks: Migrate Database Migrations to Liquibase

**Input**: Design documents from `/specs/002-liquibase-migration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Not explicitly requested in the spec. This feature has no new business logic to unit-test — verification tasks below run the existing Testcontainers suite unchanged and execute the manual scenarios from `quickstart.md` instead of adding new automated tests.

**Organization**: Tasks are grouped by user story (from `spec.md`) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Paths are relative to the repository root

## Path Conventions

Web app layout per `plan.md`: `backend/src/main/resources/...` for migration/config files, `README.md` at repo root. `frontend/` is untouched by this feature.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Swap the Maven dependency and prepare the new changelog location

- [X] T001 Replace the Flyway dependencies (`org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`) with a single `org.liquibase:liquibase-core` dependency in `backend/pom.xml`
- [X] T002 [P] Create the `backend/src/main/resources/db/changelog/` directory (new Liquibase convention, alongside the existing `db/migration/` which is removed in Phase 3)

**Checkpoint**: Dependency and directory ready; no behavior change yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire Spring Boot to use Liquibase instead of Flyway — required before any user story can be verified

**⚠️ CRITICAL**: No user story work can be verified until this phase is complete

- [X] T003 In `backend/src/main/resources/application.yml`, remove the `spring.flyway` block and add:
  ```yaml
  spring:
    liquibase:
      enabled: true
      change-log: classpath:db/changelog/db.changelog-master.yaml
  ```
  (depends on T001)

**Checkpoint**: Spring Boot is configured to look for a Liquibase master changelog — user story implementation can now begin.

---

## Phase 3: User Story 1 - Zero-touch startup still works after the tooling swap (Priority: P1) 🎯 MVP

**Goal**: The database schema and seed data are created automatically via Liquibase on `docker-compose up`, with no manual step, exactly reproducing what Flyway produces today.

**Independent Test**: On a clean environment with no existing database volume, run `docker-compose up` and verify the resulting schema/data via `quickstart.md` Scenario 1; verify restart idempotency via Scenario 2.

### Implementation for User Story 1

- [X] T004 [P] [US1] Create `backend/src/main/resources/db/changelog/001-schema.sql` as a Liquibase formatted-SQL changeset (`--liquibase formatted sql`, `--changeset tbm:1`) reproducing `backend/src/main/resources/db/migration/V1__schema.sql` exactly: `tenant`, `app_user`, `user_tenant_membership`, `pessoa`, `beneficiario` tables and the `idx_beneficiario_tenant_pessoa` index, per `data-model.md`
- [X] T005 [P] [US1] Create `backend/src/main/resources/db/changelog/002-seed-demo-data.sql` as a Liquibase formatted-SQL changeset (`--changeset tbm:2`) reproducing `backend/src/main/resources/db/migration/V2__seed_demo_data.sql` exactly: 2 tenants, 2 app_users, 3 `user_tenant_membership` rows, 4 pessoas, 4 beneficiarios; `created_at`/`updated_at` columns must remain populated by `now()` at insert time (not a literal copied timestamp), per spec.md FR-002 and `data-model.md`
- [X] T006 [US1] Create `backend/src/main/resources/db/changelog/db.changelog-master.yaml` with ordered `include` entries for `001-schema.sql` then `002-seed-demo-data.sql` (depends on T004, T005)
- [X] T007 [US1] Delete `backend/src/main/resources/db/migration/V1__schema.sql` and `backend/src/main/resources/db/migration/V2__seed_demo_data.sql` (depends on T006, after confirming the new changesets reproduce their content)
- [X] T008 [US1] Run `docker-compose down -v && docker-compose up --build` and, per `quickstart.md` Scenario 1, verify via `docker-compose exec db psql -U tbm -d tbm -c "\dt"` that `databasechangelog`/`databasechangeloglock` plus all 5 business tables exist, via `SELECT count(*)` that row counts match `data-model.md` (2 tenant, 2 app_user, 3 membership rows, 4 pessoa, 4 beneficiario), via `\d+ <table>` for each of the 5 business tables that column types, `CHECK`/`UNIQUE`/foreign-key constraints, and the `idx_beneficiario_tenant_pessoa` index match `data-model.md`/the original `V1__schema.sql` exactly (FR-002/SC-002 full structural fidelity, not just row counts), and that `hibernate.ddl-auto` in `application.yml` is still `validate` (FR-001 — confirms Hibernate is not the mechanism applying schema changes) (depends on T003, T007)
- [X] T009 [US1] Verify the demo login end-to-end at `http://localhost:8081`: `ana`/`demo123` can select both Tenant Alfa and Tenant Beta; `bruno`/`demo123` can select only Tenant Alfa — per `quickstart.md` Scenario 1 (depends on T008)
- [X] T010 [US1] Run `docker-compose restart backend` and inspect `docker-compose logs backend` to confirm Liquibase reports no new changesets applied and the backend starts healthy — per `quickstart.md` Scenario 2, US1 acceptance scenario 3 (depends on T008)
- [X] T010a [US1] Add a throwaway third changeset to `db.changelog-master.yaml`, restart the backend, and confirm only the new changeset is applied (the first two are not re-run) and startup succeeds; remove the throwaway changeset afterward — per `quickstart.md` Scenario 2 extended check, US1 acceptance scenario 4, FR-004 (depends on T010)

**Checkpoint**: User Story 1 is fully functional — `docker-compose up` produces an identical schema and seed dataset via Liquibase, and restarts are idempotent. This is the MVP.

---

## Phase 4: User Story 2 - Old migration tooling is fully removed (Priority: P2)

**Goal**: No leftover Flyway dependency, configuration, or migration files remain anywhere in the codebase.

**Independent Test**: Search the codebase and dependency manifest for Flyway references per `quickstart.md` Scenario 3 (current working tree only, not git history, per spec.md SC-003), and confirm the project still builds.

### Implementation for User Story 2

- [X] T011 [P] [US2] Run `grep -ri "flyway" backend/pom.xml backend/src/main/resources/application.yml` and confirm zero matches, per `quickstart.md` Scenario 3 (depends on T001, T003)
- [X] T012 [P] [US2] Run `find backend/src/main/resources/db -iname "V*__*.sql"` and confirm zero matches (old Flyway-named files gone), per `quickstart.md` Scenario 3 (depends on T007)
- [X] T013 [US2] Run `mvn -f backend/pom.xml clean package` and confirm the build succeeds with only `liquibase-core` on the classpath (depends on T011, T012)

**Checkpoint**: Flyway is fully absent from dependencies, config, and migration files; the project still builds cleanly.

---

## Phase 5: User Story 3 - Documentation reflects the current migration tool (Priority: P3)

**Goal**: Project documentation names Liquibase, not Flyway, wherever schema management is described.

**Independent Test**: Search project documentation for the old tool's name and confirm it's gone, per `quickstart.md` Scenario 5.

### Implementation for User Story 3

- [X] T014 [P] [US3] In `README.md`, update the line "As migrações Flyway (schema + dados de demonstração) rodam automaticamente na inicialização." to name Liquibase instead of Flyway
- [X] T015 [US3] Run `grep -i "flyway" README.md` (expect zero matches) and `grep -i "liquibase" README.md` (expect at least one match), per `quickstart.md` Scenario 5, US3 acceptance scenario 2 (README.md is the only doc location today — confirmed by a repo-wide search) (depends on T014)

**Checkpoint**: All three user stories are independently functional — documentation matches the running system.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification spanning all stories

- [X] T016 [P] Run `cd backend && mvn test` (Testcontainers-backed) and confirm the existing suite passes unchanged against the Liquibase-managed schema (depends on T008)
- [X] T017 Run `quickstart.md` Scenario 4: edit an already-applied changeset (e.g., a comment in `001-schema.sql`), `docker-compose restart backend`, confirm Liquibase's checksum-validation error causes a loud startup failure per spec.md FR-005 and the Edge Cases bullet on modifying an already-applied entry, then revert the edit (depends on T008)
- [X] T018 Using the build output already produced by T013, confirm no compiled/generated copies of the old `V1__schema.sql`/`V2__seed_demo_data.sql` remain under `backend/target/`, per spec.md FR-006 (depends on T013 — reuses its build, does not re-run `mvn clean package`)

**Checkpoint**: Feature complete — all quickstart.md scenarios pass, existing tests pass, and no Flyway trace remains anywhere including build output.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately. T001 and T002 touch different files and can run in parallel.
- **Foundational (Phase 2)**: Depends on T001 — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational (T003) completion. T004/T005 are parallel; T006 depends on both; T007 depends on T006; T008 depends on T007; T009/T010 depend on T008; T010a depends on T010.
- **User Story 2 (Phase 4)**: Its verification tasks depend on the removal work already done in Setup (T001) and User Story 1 (T007) — T011/T012 can run in parallel with each other once those complete; T013 depends on both.
- **User Story 3 (Phase 5)**: Independent of US1/US2 — only touches `README.md`. Can be done any time after the feature's tool choice is decided (i.e., immediately).
- **Polish (Phase 6)**: T016/T017 depend on User Story 1 (T008); T018 depends on User Story 2 (T013) and reuses its build output rather than rebuilding.

### Parallel Opportunities

- T001 and T002 (Setup)
- T004 and T005 (US1 — different new files)
- T011 and T012 (US2 — different checks)
- T014 (US3) can run in parallel with almost everything else — it only touches `README.md`
- T016 can run in parallel with T011/T012/T013 (US2) since it only depends on T008; T018 is sequential after T013 (inspects T013's build output, so must not race a concurrent rebuild)

---

## Parallel Example: User Story 1

```bash
# Launch the two changeset files together (different files, no dependency between them):
Task: "Create 001-schema.sql Liquibase changeset in backend/src/main/resources/db/changelog/001-schema.sql"
Task: "Create 002-seed-demo-data.sql Liquibase changeset in backend/src/main/resources/db/changelog/002-seed-demo-data.sql"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: `docker-compose up` from a clean volume produces the identical schema/seed data via Liquibase, restarts are idempotent
5. This is a deployable MVP — the tooling swap already works end-to-end

### Incremental Delivery

1. Setup + Foundational → dependency and wiring in place
2. User Story 1 → zero-touch startup works with Liquibase (MVP)
3. User Story 2 → confirm Flyway fully gone, build still clean
4. User Story 3 → docs updated
5. Polish → full test suite + all quickstart scenarios + build-output check

### Suggested Solo Order

Given this is a small, tightly-scoped infrastructure swap (not a multi-developer feature), work top-to-bottom T001 → T018 in a single pass rather than parallelizing across people — the parallel markers mainly save wall-clock time within a single session (e.g., writing both changeset files back-to-back before validating).

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- This feature has no `contracts/` artifacts (no REST/API surface change), so no contract tests are generated
- Commit after each checkpoint (end of each phase) or logical group
- Revert the deliberate edit in T017 immediately after confirming the failure behavior — do not leave the changelog checksum broken
