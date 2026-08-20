# Data Model: Searchable Select Filters

No new entity, attribute, relationship, or migration. This feature only changes a query predicate
and a result-set shape on an existing entity.

## AppUser (existing — `backend/src/main/java/com/tbm/user/AppUser.java`)

No field changes. The `username` field (already `unique = true`, non-null) is the one searched.

**Query behavior change**:

| | Before | After |
|---|---|---|
| Match type | Exact (`findByUsername` → `Optional<AppUser>`, 0 or 1 row) | Case-insensitive substring (`LIKE '%text%'`), 0–20 rows |
| Minimum input | None (any non-empty string queries) | 2 characters (trimmed); shorter input short-circuits to an empty result without querying |
| Result cap | N/A (at most 1 row possible) | 20 rows (`Pageable`), ordered by `username ASC` |

## UserSummary (existing — `backend/src/main/java/com/tbm/user/dto/UserSummary.java`)

Unchanged: `record UserSummary(UUID id, String username)`. Still the response shape for
`GET /api/users`; only how many of them can come back, and which ones match, changes.

## Pessoa (existing — unaffected)

No change. Already searched by case-insensitive substring on `nome` via
`PessoaRepository.findByNomeContainingIgnoreCase`. This feature's frontend work changes how the
*result* of that existing search is presented (one combined field instead of two), not the query
itself.

## Frontend: `SearchableSelect.vue` (new component — not a persisted entity)

An in-memory UI concept, documented here only because it's the shape both refactored views bind
to:

- **`modelValue`** (prop, `v-model`): the selected item's id (`string \| null`) — mirrors what a
  native `<select v-model="...">` already holds today (`form.pessoaId`).
- **`search`** (prop, function `(query: string) => Promise<Array<{ id, label }>>`): supplied by
  the caller, backed by the existing `pessoaApi.list`/`tenantAdminApi.searchUsers`-based store
  calls — no new API/service module.
- **`optionLabel`** (prop, function `(option) => string`, optional): how a matched option is
  rendered/formatted (e.g., `"${pessoa.nome} (${pessoa.cpf})"`, matching the current Pessoa
  `<option>` text exactly).
- **`initialLabel`** (prop, string, optional): the label to show pre-selected on load before any
  search has run (needed for the Beneficiário edit form, where the linked Pessoa must show without
  the user searching first — spec FR-007).
