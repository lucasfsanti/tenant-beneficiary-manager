# Tasks: Searchable Select Filters

**Input**: Design documents from `/specs/009-searchable-select-filters/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md (all
present)

**Tests**: Feature 008 established that every branch this codebase adds gets its own asserting
test (100% line/branch coverage is a standing project requirement, not opt-in) — task tests below
are not optional.

**Organization**: Tasks are grouped by user story per spec.md (US1 = P1 substring search, US2 = P2
the combined search-and-select field). There is no Foundational phase: US1 is entirely backend and
US2 is entirely frontend, with no shared blocking prerequisite beyond both stacks starting from a
green baseline (Setup).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Every description below includes the exact file path(s) touched

## Path Conventions

Backend paths are relative to the repo root under `backend/`; frontend paths under `frontend/`.

---

## Phase 1: Setup

**Purpose**: Confirm both test suites are currently green before changing anything, so any later
failure is attributable to this feature's own changes.

- [X] T001 [P] Run `cd backend && mvn test` and confirm all tests currently pass.
- [X] T002 [P] Run `cd frontend && npm test` and confirm all tests currently pass.

---

## Phase 2: User Story 1 - Every search field matches partial, anywhere-in-the-text input (Priority: P1) 🎯 MVP

**Goal**: Username search becomes substring/case-insensitive, gains a server-enforced 2-character
minimum and 20-result cap; the Pessoa and Beneficiário name filters' existing substring behavior
is proven by real regression tests instead of only assumed from reading the query code.

**Independent Test**: Run `cd backend && mvn test`; call `GET /api/users?username=<fragment>` and
confirm a middle-of-username fragment matches, fewer than 2 characters returns `[]`, and more than
20 matches are capped to 20; call `GET /api/pessoas?nome=<middle-fragment>` and
`GET /api/beneficiarios?pessoaNome=<middle-fragment>` and confirm matches (quickstart.md steps
1-2, 5).

- [X] T003 [US1] Add `searchByUsername(String username, Pageable pageable)` to
      `backend/src/main/java/com/tbm/user/AppUserRepository.java`: an `@Query` using
      `LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))`, ordered by `username ASC`,
      returning `List<AppUser>` (research.md §2 — mirrors `BeneficiarioRepository.search`'s
      existing pattern).
- [X] T004 [US1] Update `UserController.search()` in
      `backend/src/main/java/com/tbm/user/UserController.java` (depends on T003): return
      `List.of()` immediately, without querying, when the trimmed `username` parameter is null,
      blank, or shorter than 2 characters (research.md §3); otherwise call
      `appUserRepository.searchByUsername(username.trim(), PageRequest.of(0, 20))` and map to
      `UserSummary` as today. Update the class's `@Tag` description from "busca de usuários...
      por username exato" to describe substring matching and the minimum-length/cap behavior
      (Constitution Principle III; contracts/README.md).
- [X] T005 [US1] Extend `backend/src/test/java/com/tbm/integration/UserLookupTest.java` (depends
      on T004): add tests asserting (a) a fragment from the *middle* of a username matches, not
      only its start, (b) matching is case-insensitive, (c) a 1-character query returns `[]`
      without matching anything, (d) more than 20 matching users are capped to exactly 20 results.
- [X] T006 [P] [US1] Extend
      `backend/src/test/java/com/tbm/integration/PessoaIntegrationTest.java`: add a test asserting
      a fragment from the *middle* of a Pessoa's name (not its start) matches, case-insensitively —
      locks in FR-001 as this feature's own guaranteed behavior rather than an unverified
      assumption (research.md §1, §7).
- [X] T007 [P] [US1] Extend
      `backend/src/test/java/com/tbm/integration/BeneficiarioFilteringTest.java`: add a test
      asserting a fragment from the *middle* of the linked Pessoa's name (not its start) matches,
      case-insensitively — locks in FR-002 (research.md §1, §7).

**Checkpoint**: Backend substring matching is complete, bounded, and regression-proven for all
three searched fields — deliverable and demoable on its own via the API, independent of any
frontend change.

---

## Phase 3: User Story 2 - Picking a Pessoa or a Tenant member is one field, not a search box plus a separate list (Priority: P2)

**Goal**: Introduce a reusable combined search-and-select field and use it to replace both
existing "type in one control, pick from a separate one" patterns: the Beneficiário form's Pessoa
picker (spec's explicit acceptance scenarios), and the Tenant form's "add member" username search
(FR-009 — this one also closes the multi-match auto-add-first-result risk that US1's substring
username search opens up).

**Independent Test**: Open the Beneficiário creation form and a Tenant's edit page's member
section; in both, type a fragment and confirm the *same* field narrows to matches and lets you
pick one, with no separate dropdown to coordinate, and no way to submit without an explicit pick
(quickstart.md steps 3-4).

- [X] T008 [US2] Create `frontend/src/components/SearchableSelect.vue`: a text `<input>` bound to
      a caller-supplied async `search(query)` prop; while focused with a non-empty result set, it
      shows the matching options (rendered via a caller-supplied `optionLabel` prop) in a list
      directly below the input. Selecting an option (click, or Enter on a keyboard-highlighted
      option) commits that option's id via `v-model`, shows its label as the field's text, and
      closes the list; typing again after a selection re-opens the narrowing list without changing
      `v-model` until a new option is picked (spec Edge Cases). An optional `initialLabel` prop
      pre-populates the visible text on mount without invoking `search`, so an edit form can show
      the current selection immediately (FR-007). No match for the current query shows a clear
      "no matches" state instead of an empty list with no feedback (data-model.md, research.md §4).
- [X] T009 [P] [US2] Create `frontend/tests/unit/SearchableSelect.spec.js` (depends on T008):
      cover — typing invokes the injected `search` function and renders its results as options;
      selecting an option updates `v-model` and the field's visible text; `initialLabel` shows on
      mount without calling `search`; typing after a selection re-opens the list without changing
      the committed value until a new pick; a query with no matches shows the "no matches" state.
- [X] T010 [US2] Refactor `frontend/src/views/BeneficiarioFormView.vue` (depends on T008): replace
      the `pessoaBusca` text input plus the separate `<select id="pessoaId">` with one
      `SearchableSelect` bound to `form.pessoaId`, using `pessoaApi.list({ nome: query, size: 20
      })` as its `search` prop and `` `${pessoa.nome} (${pessoa.cpf})` `` as `optionLabel`
      (matching today's option text). On edit-mode load, pass the already-fetched Pessoa's name as
      `initialLabel` instead of the current fetch-then-splice-into-options workaround (FR-007).
      Remove the now-unused `pessoaBusca`/`onPessoaSearch`/debounce code and the
      `pessoaOptions`-splicing branch in `onMounted`.
- [X] T011 [US2] Update `frontend/tests/unit/BeneficiarioFormView.spec.js` (depends on T010):
      replace assertions against the old two-control DOM structure with assertions against the
      `SearchableSelect`-based field — typing narrows options via `pessoaApi.list` (FR-004),
      selecting one sets `form.pessoaId`, the edit form pre-shows the linked Pessoa's name on load
      without a search (FR-007), and the form cannot be submitted with a typed-but-unselected
      value (FR-006).
- [X] T012 [US2] Refactor `frontend/src/views/TenantFormView.vue` (depends on T008): replace the
      "add member" `<input v-model="newMemberUsername">` with a `SearchableSelect` using the
      `tenant` store's existing `searchUser(query)` action as its `search` prop and
      `user.username` as `optionLabel`. Change `handleAddMember()` to add the field's *selected*
      user id instead of blindly adding `results[0].id` from a fresh search (research.md §5,
      FR-009); disable/no-op the add action when nothing is selected.
- [X] T013 [US2] Update `frontend/tests/unit/TenantFormView.spec.js` (depends on T012): replace
      the "calls tenant.addMember when the add-member form is submitted" test's assumptions with
      the new explicit-selection flow (search, pick one specific match out of several, then
      submit), and add a case proving that submitting without picking a match does not call
      `addMember` — this is the automated proof of SC-003 (never silently adding the wrong user).

**Checkpoint**: Both search-and-select consumers now share one field, backed by real substring
matching, with no way to submit an unselected value.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Full-stack regression pass and manual confirmation against the running app.

- [X] T014 Run `cd backend && mvn test` and `cd frontend && npm test` one final time (depends on
      all prior tasks) and confirm both remain green. Per feature 008's standing coverage
      requirement, open both coverage reports and confirm 100% line/branch coverage (minus their
      existing documented exclusions) — every new branch this feature adds (below-minimum-length,
      at/over-cap, selection-required, pre-selected edit value, no-match empty states in
      `SearchableSelect`) needs its own asserting test, not incidental exercise.
- [X] T015 [P] Walk through `quickstart.md` end-to-end against the running stack
      (`docker-compose up`) to confirm every manual scenario — substring matching, the enumeration
      guard (2-char minimum, 20-result cap), both merged search-and-select fields, and no
      regression in the untouched Status/Tipo filters — holds on a real running system, not just
      in the automated suites.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **User Story 1 (Phase 2)**: Depends on Setup (T001). No dependency on US2 — entirely backend.
- **User Story 2 (Phase 3)**: Depends on Setup (T002). Does **not** functionally depend on US1's
  backend change to build or unit-test `SearchableSelect.vue` or the two refactored views (their
  tests mock the search functions) — but see the MVP caveat below before shipping US2 without US1.
- **Polish (Phase 4)**: Depends on all desired user stories being complete.

### Within Each Phase

- US1: T003 → T004 → T005 (same file lineage: repository, then controller, then its test).
  T006 and T007 are independent of T003–T005 and of each other (different, already-passing
  endpoints) — fully parallel with the rest of Phase 2.
- US2: T008 must exist before T009, T010, or T012 (all consume the new component). T009 is
  independent of T010–T013 (different file). T010 → T011 (same feature, refactor then its test).
  T012 → T013 (same feature, refactor then its test). T010/T011 and T012/T013 are independent of
  each other (different views) once T008 is done.

### Parallel Opportunities

- T001 and T002 (Setup, different stacks).
- T006 and T007 (Pessoa/Beneficiário regression tests — independent files, independent of the
  rest of US1).
- T009 (component tests) alongside T010–T013 (the two view refactors), once T008 is done.
- T010/T011 (Beneficiário form) and T012/T013 (Tenant form) can be worked on in parallel by
  different people once T008 is done — different views, no shared file.

---

## Parallel Example: User Story 1

```bash
Task: "Add AppUserRepository.searchByUsername(...)"
Task: "Update UserController.search() to use it, with the min-length guard and Swagger update"
Task: "Extend UserLookupTest.java for substring/case/min-length/cap"
# In parallel with the above three:
Task: "Extend PessoaIntegrationTest.java with a middle-of-name substring regression test"
Task: "Extend BeneficiarioFilteringTest.java with a middle-of-name substring regression test"
```

## Parallel Example: User Story 2

```bash
Task: "Create SearchableSelect.vue"
# Once done, in parallel:
Task: "Create SearchableSelect.spec.js"
Task: "Refactor BeneficiarioFormView.vue + its spec"
Task: "Refactor TenantFormView.vue + its spec"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: User Story 1 — username search is substring-based, bounded, and regression-
   tested at the API level.
3. **STOP and VALIDATE**: run T005–T007, confirm the substring/min-length/cap behavior end-to-end
   via `GET /api/users`.

**Caveat before shipping US1 alone**: US1 makes username search substring-based, which means the
*existing* Tenant "add member" flow (still auto-adding `results[0].id` until US2 refactors it)
can now silently add the wrong person when a search matches more than one user — this is exactly
the risk FR-009/SC-003 exist to close. Treat US1 and US2 as a paired release unless that window is
explicitly acceptable; do not ship US1's backend change to production ahead of US2's `TenantFormView`
refactor (T012–T013) without also restricting username search back to a stricter minimum length in
the meantime.

### Incremental Delivery

1. Setup → both suites confirmed green.
2. Add User Story 1 → username search reported correct, bounded, and regression-tested (mind the
   caveat above regarding the existing add-member flow).
3. Add User Story 2 → both search-and-select fields merged, add-member flow made explicit-selection
   safe.
4. Polish → full regression pass on both stacks, manual quickstart walkthrough.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- US1's regression tests (T006, T007) exist because reading the query code isn't proof of
  behavior — the existing tests for Pessoa/Beneficiário search only ever searched a name's
  *prefix* in practice, never a true middle-of-string fragment or a case-differing one
  (research.md §7).
- US2's two view refactors (T010–T013) are independent of each other but both depend on the same
  new component (T008) — build the component and its own tests first.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently.
