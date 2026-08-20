# Quickstart: Searchable Select Filters

Manual/exploratory validation for this feature, once implemented. Assumes the stack is already
running (`docker-compose up`) and you're logged in as a seeded demo user.

## 1. Username search now matches substrings, not just exact usernames

1. Go to **Administradores do Sistema** (System Admin only).
2. Type a fragment of a known username that is neither the start nor the whole username (e.g.,
   part of the middle of `ana.silva`, such as `.sil`).
3. **Expect**: the matching user(s) appear, exactly as if you'd searched a Pessoa or Beneficiário
   by a name fragment today.
4. Type a single character.
5. **Expect**: no search runs yet (no results shown, no "not found" message) — you need at least 2
   characters.
6. Open the Network tab, type a single character, and confirm no `GET /api/users` request fires at
   all until a second character is typed.

## 2. Username search is capped, not a full user listing

1. Still on **Administradores do Sistema** (or the Tenant "add member" search, step 3 below), type
   a single common letter likely to match many accounts (e.g., `a`, once past the 2-character
   minimum — try `an`).
2. **Expect**: at most 20 results, even if more than 20 usernames actually contain that text.

## 3. Adding a Tenant member requires picking a specific user, not just searching

1. Go to a Tenant's edit page (**Tenants → editar** any tenant, System Admin or that tenant's
   Tenant Admin) and find the "Adicionar" member section.
2. Type a username fragment that matches more than one account.
3. **Expect**: a list of the matching users appears in the same field (not a separate box), and
   the "Adicionar" action is only available once you've picked one specific user from that list —
   not automatically applied to whichever result happened to come first.
4. Add the member and confirm the correct, chosen user appears in the members table below.

## 4. Picking a Pessoa for a Beneficiário is one field, not two

1. Go to **Beneficiários → Novo Beneficiário**.
2. Start typing a Pessoa name fragment into the Pessoa field.
3. **Expect**: matching Pessoa options appear directly in that same field as you type — there is
   no separate search box above a separate dropdown.
4. Pick one. **Expect**: the field now shows the selected Pessoa's name, and the form can be
   submitted.
5. Try submitting without picking an option after typing (e.g., type text matching nothing, or
   type and don't click a result). **Expect**: the form does not accept it as a valid Pessoa.
6. Open an existing Beneficiário for editing. **Expect**: the Pessoa field already shows the
   currently-linked Pessoa's name immediately on load, with no search needed to see who's
   currently selected.

## 5. Existing filters still work at least as well as before

1. On **Pessoas**, filter by a name fragment (not the start of the name). **Expect**: matches, as
   today.
2. On **Beneficiários**, filter by a Pessoa-name fragment and by Status. **Expect**: both filters
   still work exactly as today (Status remains a plain dropdown — no search field, unaffected by
   this feature).

## 6. No regressions in the automated suites

```bash
cd backend && mvn test    # UserLookupTest covers the substring/min-length/cap behavior
cd frontend && npm test   # SearchableSelect.spec.js, updated BeneficiarioFormView/TenantFormView specs
```

Both MUST remain green, and — per feature 008's standing coverage requirement — both coverage
reports should stay at 100% (minus their existing documented exclusions), since every new
branch introduced here (below-minimum-length, at/over-cap, selection-required, pre-selected edit
value) needs its own asserting test, not just incidental exercise.
