# Research: Searchable Select Filters

## 1. Current state of "LIKE" filtering — what's actually new work

**Finding**: `PessoaRepository.findByNomeContainingIgnoreCase(nome, Pageable)` and
`BeneficiarioRepository.search(pessoaNome, status, Pageable)` (its `LOWER(p.nome) LIKE
LOWER(CONCAT('%', :pessoaNome, '%'))` predicate) already implement case-insensitive substring
matching. `BeneficiarioFormView.vue`'s Pessoa search reuses `pessoaApi.list({ nome, size: 20 })`,
so it already benefits from the same substring behavior.

The only field that is genuinely exact-match today is username search:
`AppUserRepository.findByUsername(String)` → `Optional<AppUser>`, called from
`UserController.search()`, used by both the System Admin screen and the Tenant "add member"
screen via the same `GET /api/users?username=` endpoint.

**Decision**: This feature's backend work for FR-001–FR-004 is regression tests only (lock in
existing behavior); the only production-code change for substring matching is username search
(FR-003).

**Rationale**: No point re-implementing what's already correct; spec explicitly frames FR-001/002
as "already the current behavior... locked in," not new work.

## 2. Username substring search — query shape

**Decision**: Add one new `AppUserRepository` method, following the exact pattern
`BeneficiarioRepository.search` already uses (an explicit `@Query` with
`LOWER(...) LIKE LOWER(CONCAT('%', :param, '%'))`), returning a `Pageable`-capped result list:

```java
@Query("SELECT u FROM AppUser u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) "
        + "ORDER BY u.username ASC")
List<AppUser> searchByUsername(@Param("username") String username, Pageable pageable);
```

Called as `searchByUsername(username.trim(), PageRequest.of(0, 20))` — `PageRequest.of(0, 20)`
caps the result at 20 rows (spec SC-005) using the same `Pageable` mechanism already used
elsewhere in this codebase (`PessoaRepository`, `BeneficiarioRepository`), rather than inventing a
new limiting mechanism.

**Alternatives considered**:
- A derived-name query (`findTop20ByUsernameContainingIgnoreCase`) — rejected only because the
  codebase's established convention for a multi-condition/explicit-cap search is the `@Query` +
  `Pageable` form (`BeneficiarioRepository.search`), and consistency with that existing pattern
  outweighs the marginal terseness of a derived name here.
- Native SQL with `LIMIT` — rejected; JPQL + `Pageable` already gets the same result without
  dropping to native SQL (native SQL in this codebase is reserved for cases JPQL genuinely can't
  express, e.g. `PessoaRepository.existsBeneficiarioReferencing`).

## 3. Where the 2-character minimum and 20-result cap are enforced

**Decision**: Server-side, in `UserController.search()` — if `username` is `null`, blank, or
shorter than 2 characters after trimming, return `List.of()` immediately (HTTP `200` with an empty
body), without querying the repository at all. The 20-result cap is enforced by the `Pageable` in
research point 2.

**Rationale**: Constitution Principle II: "the API is the actual trust boundary." The whole point
of the 2-char-minimum/20-cap clarification (spec Clarifications) is to prevent trivial
enumeration of every account in the system; if that were only a front-end debounce/guard, anyone
calling the endpoint directly (curl, devtools) would bypass it entirely, defeating the purpose.
An empty list (not a `400`) for below-minimum input is not a validation *failure* — it is a
normal, expected "not enough to search yet" state (spec Edge Cases), so no RFC 7807 Problem Detail
is warranted per Constitution Principle II's own distinction between foreseeable-but-invalid input
and a genuine error.

**Alternatives considered**:
- `400 Bad Request` for short queries — rejected; this isn't a client error, it's an expected
  intermediate state while typing (same reasoning the spec's edge case gives for "no search runs
  yet" rather than an error).
- Front-end-only enforcement — rejected as the sole mechanism for the reason above; it remains
  valuable as a UX/network-efficiency optimization (skip firing the request while below the
  minimum), layered on top of, not instead of, the server-side guard.

## 4. Merging "search box" + "results select" into one field

**Decision**: A new, small, reusable Vue component (`frontend/src/components/SearchableSelect.vue`)
built from existing primitives — a text `<input>` plus a conditionally-rendered list of matching
options rendered directly below it (shown while the input has focus and there are matches;
clicking or keyboard-selecting an option commits it as the field's value and closes the list).
It takes a caller-supplied async `search(query)` function (so both call sites plug in their
existing `pessoaApi.list`/`tenantAdminApi.searchUsers`-backed store calls without new API surface)
and an `option-label` accessor, and emits the selected item's id via `v-model`, mirroring how a
native `<select>` is used today in this codebase (`v-model="form.pessoaId"` becomes
`v-model="form.pessoaId"` on the new component instead).

**Alternatives considered**:
- **A UI-library combobox/autocomplete component** (e.g., a `vue-select`-style package) — rejected
  per Constitution Principle V ("do not introduce speculative abstractions... that the stated
  scope does not call for"): this app has exactly two use sites for this pattern, and both already
  have an async "search by text" call ready to plug in; a new dependency's own configuration
  surface and bundle-size cost isn't justified for that scope, and would be a stack deviation
  requiring README justification the project doesn't otherwise need.
- **Native `<input list="…"> <datalist>`** — rejected: a `<datalist>`-backed input's *value* is
  free text the browser does not force to match an option, which cannot satisfy FR-006's "MUST
  NOT accept free-typed text that doesn't correspond to a selection" without extra validation
  layered back on top anyway; it also has no clean way to carry a hidden id alongside a visible
  label (this app's options are "name (id)" pairs, not id-equals-label), and cross-browser styling
  of the suggestion list is not controllable, unlike this app's existing plain-CSS `<select>`/
  `<input>` look.
- **Two Vue components (one per call site) instead of one shared component** — rejected; the
  Pessoa picker and the "add member" user picker are functionally identical to a reusable
  component (type, see matches, pick one, form gets an id) — a single shared component is less
  code than two near-duplicates and is more consistent with existing shared components in this
  codebase (`ErrorBanner.vue`, `PaginationControl.vue`).

## 5. `TenantFormView.vue`'s "add member" flow — behavior change, not just a new field

**Finding**: Today, `handleAddMember()` calls `store.searchUser(newMemberUsername.value)` and
blindly adds `results[0].id` — safe only because exact-match search returns at most one result.
Once username search is substring-based (research point 2), that same code could silently add the
wrong person out of several matches.

**Decision**: `handleAddMember()` changes from "search, then auto-add the first/only result" to
"the `SearchableSelect` field holds the chosen user; the Adicionar button adds *that* selection" —
the search-then-pick step and the add step become two explicit actions instead of one implicit
one, consistent with spec FR-009.

**Rationale**: Directly required by spec FR-009 and SC-003 ("Adding a member... never adds the
wrong user — the person... always confirms which specific matching user is added").

## 6. Swagger/OpenAPI documentation update

**Finding**: `UserController` carries `@Tag(name = "Usuários", description = "Busca de usuários
existentes por username exato (seletor de membros)")` — "exato" (exact) becomes inaccurate once
matching is substring-based.

**Decision**: Update the `@Tag` description to describe substring matching and the minimum-length/
result-cap behavior, in the same change that alters the controller's matching logic (Constitution
Principle III: contract changes MUST be reflected in documentation in the same change).

## 7. Existing test coverage to extend, not duplicate

**Finding**: `backend/src/test/java/com/tbm/integration/UserLookupTest.java` already exists with
two tests (`findsExistingUserByExactUsername`, `returnsEmptyListWhenUsernameNotFound`) against the
same endpoint. `frontend/tests/unit/BeneficiarioFormView.spec.js` and
`frontend/tests/unit/TenantFormView.spec.js` already exist (from spec 008's 100%-coverage work)
and currently assert against the *current* two-control Pessoa picker and the *current* auto-add
member flow.

**Decision**: Extend `UserLookupTest.java` in place (substring match, below-minimum-length,
over-cap, case-insensitivity cases) rather than creating a parallel test file. The two frontend
spec files' existing tests that exercise the old DOM structure/auto-add behavior will need
updating to match the new `SearchableSelect`-based structure and explicit-selection flow — this is
expected, tracked work for `/speckit-tasks`, not a sign of an design problem.
