---

description: "Task list template for feature implementation"
---

# Tasks: Clearer, Expanded Demo Seed Data

**Input**: Design documents from `/specs/005-seed-data-relabel/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/README.md, quickstart.md (all present)

**Tests**: This feature's own success criteria (SC-003: existing automated verification keeps
passing) make regression-gate tasks essential — they are framed as *regression gates*, not
classic TDD, since there's no new user-facing behavior to write a failing test against first.

**Organization**: Tasks are grouped by user story per spec.md (US1 = self-describing naming, US2 =
broader demo coverage). The migration-split + optional-seeding mechanism (spec FR-013/014/015),
which has no dedicated user story of its own — it's plumbing both stories' data changes sit on top
of — lives in Phase 2 (Foundational), with its own end-to-end verification in the final Polish
phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Every description below includes the exact file path(s) touched

## Path Conventions

Backend-only feature (no frontend changes). All paths are relative to the repository root, under
`backend/src/main/resources/db/changelog/`, `backend/src/main/resources/application.yml`,
`backend/src/test/java/com/tbm/integration/`, and `README.md`.

---

## Phase 1: Setup

**Purpose**: Establish the pre-migration baseline this feature's zero-regression success criteria
(SC-003) are measured against.

- [X] T001 Run the full backend test suite (`cd backend && mvn clean test
      -Dnet.bytebuddy.experimental=true`, with `JAVA_HOME=~/.local/opt/jdk-21.0.4+7`) and confirm
      it is currently all-green before any change.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Split the one existing changeset that mixes schema and demo-data (spec FR-013,
Clarifications Option A) and wire up the `demo` profile/Liquibase-context mechanism (FR-014/015)
that every demo-data changeset — old and new — will be gated by.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] In `backend/src/main/resources/db/changelog/003-role-system.sql`: remove the seed
      `INSERT` (the System Admin user) and `UPDATE` (bruno's `is_tenant_admin` flag) statements,
      keeping only the two `ALTER TABLE` statements; update the file's leading comment to reflect
      that it is now schema-only (research.md §2).
- [X] T003 [P] Create `backend/src/main/resources/db/changelog/004-role-system-seed-data.sql`:
      `--liquibase formatted sql`, changeset `tbm:4 context:demo`, containing exactly the
      `INSERT`/`UPDATE` statements removed from `003` in T002, unchanged in content (research.md
      §2).
- [X] T004 [P] In `backend/src/main/resources/db/changelog/002-seed-demo-data.sql`: change the
      changeset header from `--changeset tbm:2` to `--changeset tbm:2 context:demo`; no other
      content changes (research.md §1).
- [X] T005 Update `backend/src/main/resources/db/changelog/db.changelog-master.yaml` to add an
      `include` for `004-role-system-seed-data.sql` immediately after the existing `003` include.
      Depends on T003.
- [X] T006 [P] In `backend/src/main/resources/application.yml`: add `spring.profiles.active: demo`
      to the base document as the default active profile; append a new YAML document (`---`
      separator) with `spring.config.activate.on-profile: demo` and `spring.liquibase.contexts:
      demo` (research.md §1).

**Checkpoint**: Foundation ready — `003` is schema-only, `004` carries the extracted seed data
under `context:demo`, `002` is tagged the same way, and the `demo` profile (active by default)
gates whether any `context:demo` changeset runs at all. Nothing about the actual demo data's
*content* has changed yet.

---

## Phase 3: User Story 1 - Self-describing demo data (Priority: P1) 🎯 MVP

**Goal**: Relabel every existing seeded Tenant/Pessoa/User/Beneficiário to the "Tenant N" / "Pessoa
N" / "User N - ROLE" / "Beneficiário N - Tenant M" convention, with every id and every user's
tenant memberships/admin standing unchanged (spec FR-001–005).

**Independent Test**: List every seeded Tenant/User/Pessoa/Beneficiário and confirm each name
alone reveals what it is (and, for Users/Beneficiários, its tenant/role) — per spec Acceptance
Scenarios 1–4 — and confirm User 2's/User 1's standing still matches bruno's/ana's original
standing exactly.

### Implementation for User Story 1

- [X] T007 [US1] Create `backend/src/main/resources/db/changelog/005-seed-data-relabel-and-expand.sql`:
      `--liquibase formatted sql`, changeset `tbm:5 context:demo`, containing `UPDATE` statements
      renaming the 2 existing tenants, 3 existing users, 4 existing pessoas, and 4 existing
      beneficiarios to their new names, matched by their existing (unchanged) ids — exact mapping
      in research.md §3 / data-model.md. Depends on T005.
- [X] T008 [US1] Update `backend/src/main/resources/db/changelog/db.changelog-master.yaml` to add
      an `include` for `005-seed-data-relabel-and-expand.sql` after `004`. Depends on T007.
- [X] T009 [US1] In `backend/src/test/java/com/tbm/integration/TenantUpdateAuthorizationTest.java`:
      replace both occurrences of the literal `"Tenant Alfa"` with `"Tenant 1"` (research.md §5).
- [X] T010 [US1] In `backend/src/test/java/com/tbm/integration/PessoaDeletionRestrictionTest.java`:
      update the anti-leak assertion's substrings from `"tenant alfa"`/`"tenant beta"` to
      `"tenant 1"`/`"tenant 2"`; update its doc comment (research.md §5).
- [X] T011 [US1] In `backend/src/test/java/com/tbm/integration/TenantIsolationTest.java`: replace
      the literal matricula `"MAT-B-001"` with `"Beneficiário 1 - Tenant 2"`; update its doc
      comment (research.md §5).
- [X] T012 [P] [US1] Update doc-comment-only references to the old demo names (zero behavioral
      change) in: `backend/src/test/java/com/tbm/integration/AbstractIntegrationTest.java`,
      `TenantCrudTest.java`, `TenantAdminGrantRevokeTest.java` (2 comments), `MembershipManagementTest.java`,
      `BeneficiarioCreationTest.java`, `TenantMembershipEnforcementTest.java`.
- [X] T013 [US1] Update `README.md`'s "Usuários de demonstração" table: rename the 3 existing rows
      to `User 1 - NORMAL` / `User 2 - TENANT ADMIN` / `User 3 - ADMIN` and their tenants to
      `Tenant 1` / `Tenant 2`. Also add a short note below the table documenting the demo-data
      toggle (spec FR-014/015): the default `docker-compose up` path seeds demo data
      automatically; starting with `SPRING_PROFILES_ACTIVE=` (empty) skips it, yielding a
      schema-only database with no possible login (see quickstart.md §4) — this is the plan.md
      Project Structure commitment cited under "Delivery & Documentation Requirements" in the
      Constitution Check, previously undelivered by any task.
- [X] T014 [US1] Run the full backend test suite (`mvn clean test`) and confirm zero regressions
      against the T001 baseline (spec SC-003). Depends on T008–T012. **Found and fixed one
      regression outside the originally-planned file list**: `BeneficiarioFilteringTest.java`
      filtered/asserted on `pessoaNome` containing `"Maria"` (a substring of the old "Maria Silva"
      name, not an exact literal my earlier grep caught) — updated both occurrences to `"Pessoa
      1"`. A full suite sweep confirmed no other pessoa-name-fragment literals remained. 102/102
      green after the fix.

**Checkpoint**: User Story 1 is fully functional and independently testable — every seeded record
reads as expected on its own, every relationship is unchanged, and the full regression suite is
green.

---

## Phase 4: User Story 2 - Broader demo coverage (Priority: P2)

**Goal**: Add Tenant 3/4, Users 4–6, Pessoa 5–8, and 3 new Beneficiário rows, broadening role
coverage (a second independent Tenant Admin, a single-tenant Normal user) without touching
anything User Story 1 already relabeled (spec FR-007–010).

**Independent Test**: Count Tenants/Users/Pessoas/Beneficiários before and after and confirm a
measurable increase; confirm User 2 and User 4 are Tenant Admins of two different tenants with no
overlap; confirm User 6 is a Normal member of exactly one tenant — per spec Acceptance Scenarios
1–3.

### Implementation for User Story 2

- [X] T015 [US2] Append `INSERT` statements for Tenant 3 and Tenant 4 to the same changeset
      (`tbm:5`) in `005-seed-data-relabel-and-expand.sql`. Depends on T007.
- [X] T016 [US2] Append `INSERT` statements for Pessoa 5–8 (using the 4 pre-computed,
      check-digit-valid CPFs from research.md §4) to the same file. Depends on T015.
- [X] T017 [US2] Append `INSERT` statements for User 4 (Tenant Admin of Tenant 3 only), User 5
      (Normal, member of Tenant 3 + Tenant 4), and User 6 (Normal, member of Tenant 2 only),
      including their `user_tenant_membership` rows, to the same file — same password hash as
      every other seeded user (research.md §4). Depends on T016.
- [X] T018 [US2] Append `INSERT` statements for Beneficiário 1 - Tenant 3 (Pessoa 5), Beneficiário
      2 - Tenant 3 (Pessoa 6), and Beneficiário 1 - Tenant 4 (Pessoa 7) to the same file. Depends
      on T017.
- [X] T019 [US2] Add the 3 new users to `README.md`'s "Usuários de demonstração" table (extending
      T013's updated table). Depends on T013, T017.
- [X] T020 [US2] Run the full backend test suite (`mvn clean test`) and confirm zero regressions
      now that the new rows are present (spec SC-003). Depends on T018, T019. **Found and fixed a
      CPF collision**: 3 of the 4 CPFs research.md §4 pre-computed for Pessoa 5/7/8
      (`12345678909`, `98765432100`, `11223344517`) turned out to already be hardcoded in
      `PessoaIntegrationTest.java` (that file's own generated-data CPFs, not part of the seed
      data) — an exhaustive `grep` for every 11-digit literal in `backend/src` (not just the ones
      already known from earlier reads) turned up the collision. Replaced all 3 with freshly
      computed, check-digit-valid, verified-non-colliding CPFs (`91283746573`, `74192638509`,
      `47296183004`); Pessoa 6's `23456789173` was already clean. 102/102 green after the fix.

**Checkpoint**: User Stories 1 and 2 both hold — the demo roster is relabeled and expanded, and
the full regression suite is green.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end verification of the migration-split/optional-seeding mechanism (spec
FR-013/014/015, SC-005) against the now-complete demo dataset, plus a live sanity check of the
relabeled/expanded data through the real API.

- [X] T021 Recreate the local Docker volume and confirm the default startup path still seeds demo
      data automatically (quickstart.md §1): `docker compose down -v && docker compose up -d
      --build`. Verify the full resulting dataset against research.md §3/§4's tables, not just
      tenants (spec SC-001/SC-002/SC-004): log in as each of the 6 seeded users (`User 1 - NORMAL`
      through `User 6 - NORMAL`) with `demo123` and confirm every login succeeds; as `User 3 -
      ADMIN`, confirm `GET /api/tenants` returns exactly 4 tenants named `Tenant 1`–`Tenant 4`
      (2→4, satisfying the ≥50% increase); spot-check one Pessoa name (`Pessoa 1`) and one
      Beneficiário label (`Beneficiário 1 - Tenant 1`) via their respective list endpoints; confirm
      every username matches the `User <N> - <ROLE>` pattern with `<ROLE>` matching that user's
      actual System Admin/Tenant Admin/Normal standing. Depends on T020. **Verified against the
      live stack**: fresh volume, all 6 logins succeeded, `/api/tenants` returned exactly the 4
      expected tenants, and a script cross-checked every user's `/api/me` (`isSystemAdmin`,
      per-tenant `isTenantAdmin`) against their username's `<ROLE>` suffix — 6/6 matched. Pessoa 1
      / Beneficiário 1 - Tenant 1 / Beneficiário 2 - Tenant 1 confirmed via
      `GET /api/beneficiarios`.
- [X] T022 Confirm demo data is skippable, and confirm the expected "clean-slate, not a usable
      deployment" outcome this implies (spec Edge Cases, quickstart.md §4): stop the stack, then
      run the backend with `SPRING_PROFILES_ACTIVE=` (empty) instead of the default; confirm it
      starts healthy (`/actuator/health` succeeds, schema fully migrated) but login fails for
      every seeded username (no demo data present) — and explicitly confirm there is no other way
      to obtain a valid login in this mode (no registration endpoint exists), matching spec
      Assumptions' documented scope boundary. Depends on T021. **Found and fixed a real bug during
      this check**: `application.yml`'s base document never set `spring.liquibase.contexts`,
      leaving it null when the `demo` profile isn't active. Liquibase treats a null/unset contexts
      filter as "no filtering — run every changeset," not "only run untagged changesets" (the
      opposite of research.md §1's assumption) — so `SPRING_PROFILES_ACTIVE=` was NOT actually
      skipping the `context:demo` changesets; the log showed 002/004/005 all running (confirmed via
      `docker logs`, a fresh `docker compose down -v` volume, and
      `docker compose run --rm -e SPRING_PROFILES_ACTIVE=`). Fixed by always setting
      `spring.liquibase.contexts` explicitly in the base document to a sentinel value
      (`no-demo-data`) that never matches any changeset's context tag, which the demo-profile
      document's `contexts: demo` still overrides when that profile is active. Re-verified against
      two fresh volumes after rebuilding the backend image: (1) demo off — Liquibase log shows
      `Filtered out: 3` (002/004/005 skipped, only 001/003 ran), and
      `POST /api/auth/login` for `User 3 - ADMIN`/`demo123` returns `401`; (2) demo on (default) —
      `Filtered out: 0` (all 5 changesets run), and the same login returns `200` with a valid JWT.
      No registration endpoint exists in the codebase (confirmed by inspection of the auth
      controller), so login is genuinely the only way in, matching the spec's documented scope
      boundary.
- [X] T023 Tear down the stack (`docker compose down`) once T021/T022 are confirmed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — run first to capture the baseline.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories (the `005` file User
  Story 1 creates must be registered after `004`, which Phase 2 creates).
- **User Story 1 (Phase 3)**: Depends on Foundational. This is the MVP.
- **User Story 2 (Phase 4)**: Depends on User Story 1 — both stories' data live in the same
  `005-seed-data-relabel-and-expand.sql` file (T015–T018 append to what T007 created), so they
  must be applied in sequence, not in parallel, despite otherwise being independent concerns.
- **Polish (Phase 5)**: Depends on Phase 4 — verifies the toggle mechanism against the complete,
  final dataset.

### Within Phase 2 (Foundational)

- T002, T003, T004, T006 touch different files — independent of each other.
- T005 depends on T003 (references the file it creates).

### Within Phase 3 (User Story 1)

- T007 depends on T005 (changelog must already include up through `004`).
- T008 depends on T007.
- T009, T010, T011 are independent of each other (different files) but all depend on T007 existing
  conceptually (the new names they reference come from it) — no file dependency, safe to do in any
  order once T007's mapping is known.
- T012 is independent of T009–T011 (different files).
- T013 has no code dependency, only needs the final name mapping (known from research.md §3).
- T014 (full suite run) depends on T008, T009, T010, T011, T012.

### Within Phase 4 (User Story 2)

- T015 → T016 → T017 → T018 are strictly sequential (same file, each appending to what the last
  left).
- T019 depends on T013 (extends the same README table) and T017 (needs the new usernames).
- T020 depends on T018 and T019.

### Parallel Opportunities

- T002, T003, T004, T006 (Phase 2) can run in parallel — four different files.
- T009, T010, T011, T012 (Phase 3) can run in parallel — four different test files, none
  conflicting.
- T015–T018 (Phase 4) CANNOT run in parallel — same file, sequential appends.

---

## Parallel Example: Phase 2 → Phase 3 test-file edits

```bash
# Phase 2, in parallel:
Task: "Split 003-role-system.sql to schema-only"
Task: "Create 004-role-system-seed-data.sql with the extracted seed data"
Task: "Tag 002-seed-demo-data.sql's changeset with context:demo"
Task: "Wire the demo Spring profile + Liquibase context in application.yml"

# Phase 3, in parallel (after T007/T008 land):
Task: "Fix Tenant Alfa literal in TenantUpdateAuthorizationTest.java"
Task: "Fix tenant-name substring check in PessoaDeletionRestrictionTest.java"
Task: "Fix MAT-B-001 literal in TenantIsolationTest.java"
Task: "Fix doc comments in the other 6 test files"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (baseline) and Phase 2 (foundational split + toggle plumbing) — required, not
   skippable.
2. Complete Phase 3 (User Story 1) — this alone delivers the entire naming-clarity value.
3. **STOP and VALIDATE**: T014's full suite run is the MVP gate.
4. Phase 4 (more data) and Phase 5 (toggle end-to-end verification) can follow immediately after,
   since Phase 2's plumbing already supports them — no rework needed.

### Incremental Delivery

1. Setup + Foundational → migration split and toggle mechanism ready, demo data content
   unchanged.
2. User Story 1 → renamed demo data, fully regression-tested (MVP).
3. User Story 2 → expanded demo data, layered onto the same migration file.
4. Polish → live confirmation that the default path still seeds automatically and the opt-out
   genuinely skips it.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps each task to spec.md's user stories for traceability.
- This feature has no frontend tasks — the two similarly-named Vitest mock files are
  self-contained and out of scope (research.md §5).
- Commit after each task or logical group.
- Avoid: touching `005-seed-data-relabel-and-expand.sql` in more than one task at a time outside
  the explicit T007→T015→T016→T017→T018 append sequence.

---

## Phase 6: Convergence

- [X] T024 Update `specs/005-seed-data-relabel/quickstart.md` §4 ("Starting without demo data") to
      replace the superseded `docker compose run --rm -e SPRING_PROFILES_ACTIVE= -p 8080:8080
      backend &` invocation with the actually-shipped, README-documented mechanism:
      `SPRING_PROFILES_ACTIVE=no-demo docker-compose up` (relying on `docker-compose.yml`'s
      `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-demo}` and `application.yml`'s named
      `no-demo` Spring profile document, both added after T022 in response to follow-up requests
      to expose the toggle as a `docker-compose up` argument rather than a `docker compose run
      --rm -e` workaround). The old command still functions correctly (verified) but no longer
      matches what README.md and `docker-compose.yml` document as the standard invocation — this
      task closes that documentation drift (FR-014, partial). **Verified against a fresh volume**
      using `docker compose` (this environment has no standalone `docker-compose` binary — only
      the plugin form — a pre-existing repo-wide convention gap from before this feature,
      README.md's very first `docker-compose up` line included, and out of this task's scope):
      `SPRING_PROFILES_ACTIVE=no-demo docker compose up -d db backend` starts healthy and
      `POST /api/auth/login` for `User 3 - ADMIN`/`demo123` returns `401` as expected.
