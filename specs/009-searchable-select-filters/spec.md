# Feature Specification: Searchable Select Filters

**Feature Branch**: `009-searchable-select-filters`

**Created**: 2026-08-20

**Status**: Draft

**Input**: User description: "Make the all the search fields a filter with LIKE. And merge the search fields that show its retsults in a select, into select with a text input to search, in the same field"

## Clarifications

### Session 2026-08-20

- Q: Should the number of users returned by a username search be bounded in some way, now that it matches partial text instead of requiring an exact username? → A: Both — require a minimum number of characters typed AND cap the number of results returned.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every search field matches partial, anywhere-in-the-text input (Priority: P1)

A user typing part of a name or username into any search or filter field on the site sees every
record that contains that text anywhere — not only records that start with it or match it
exactly — the same way the Pessoa and Beneficiário name filters already behave today.

**Why this priority**: This is the more far-reaching change (it touches every search field in the
application, including one — user search by username — that currently requires an exact match)
and is the foundation the combined search-and-select control in User Story 2 depends on.

**Independent Test**: On every screen with a search or filter field, type a fragment of a known
record's name or username (not the beginning, not the whole value) and confirm the matching
record(s) appear.

**Acceptance Scenarios**:

1. **Given** the Pessoas list, **When** a user types a substring that appears in the middle of a
   Pessoa's name, **Then** that Pessoa appears in the filtered results.
2. **Given** the Beneficiários list, **When** a user types a substring that appears in the middle
   of a Pessoa's name, **Then** every Beneficiário linked to a matching Pessoa appears in the
   filtered results.
3. **Given** the System Admin search screen, **When** an admin types a substring that appears
   anywhere in a username (not just its start), **Then** every user whose username contains that
   substring appears in the results, not only an exact match.
4. **Given** the Tenant member-management screen's "add member" search, **When** a Tenant Admin or
   System Admin types a substring that appears anywhere in a username, **Then** every user whose
   username contains that substring is found, not only an exact match.
5. **Given** any of the search fields above, **When** the typed text matches no record, **Then**
   the user sees a clear "no results" state rather than an error or a stale list.
6. **Given** any of the search fields above, **When** the search is case-different from the stored
   value (e.g., searching "ana" against a record named "Ana"), **Then** the record still matches.

---

### User Story 2 - Picking a Pessoa or a Tenant member is one field, not a search box plus a separate list (Priority: P2)

When creating or editing a Beneficiário, the person filling out the form searches for and picks
the associated Pessoa using a single field: start typing a name, and choose the right person from
the narrowing list right there — instead of typing into one box and then having to look at a
second, separate dropdown to make the actual selection. The same combined field is used when a
Tenant Admin or System Admin adds a member to a Tenant by username: search and pick a specific
matching user, rather than having a search that (once it can match more than one person) leaves
no way to say which one was meant.

**Why this priority**: This is a smaller, more contained change than User Story 1 (it touches two
forms) and is a usability refinement layered on top of the substring search behavior US1
establishes — it doesn't need to ship first for the Pessoa field, but the Tenant "add member"
field specifically depends on US1's substring username search to matter: today that search can
only ever find at most one exact match, so nothing depends on a picker yet.

**Independent Test**: Open the Beneficiário creation form, type a partial Pessoa name into the
Pessoa field, and confirm the same field both narrows to matching people and lets the user pick
one — with no separate search box and dropdown to coordinate. Separately, open a Tenant's edit
page, type a partial username into the "add member" field, and confirm it likewise narrows to
matching users and requires picking one before a member can be added.

**Acceptance Scenarios**:

1. **Given** the Beneficiário creation/edit form, **When** the user starts typing into the Pessoa
   field, **Then** matching Pessoa records appear as selectable options within that same field.
2. **Given** the Pessoa field with a set of matching options showing, **When** the user selects
   one, **Then** that Pessoa becomes the form's chosen value and the field shows the selected
   Pessoa's name.
3. **Given** the Beneficiário edit form for an existing record, **When** the form loads, **Then**
   the Pessoa field already shows the currently-associated Pessoa's name, without requiring the
   user to search again to see who is currently selected.
4. **Given** the Pessoa field, **When** the typed text matches no Pessoa, **Then** the field shows
   a clear "no matches" state and does not allow submitting an unselected/invalid Pessoa.
5. **Given** the Tenant edit page's "add member" field, **When** a search matches more than one
   user, **Then** every match appears as a selectable option within that same field, and the
   person adding a member must pick one specific user before the add action is available — no
   result is ever added automatically on their behalf (FR-009, SC-003).

---

### Edge Cases

- What happens when the search text is empty or only whitespace? Filter fields show all records
  (unfiltered), matching today's behavior for the Pessoa and Beneficiário name filters.
- What happens when a search returns a very large number of matches (e.g., a single common
  letter)? The existing result-size limits already in place for each search (pagination for list
  filters, a capped result count for the Pessoa picker) continue to apply — this feature changes
  *which* records match, not how many are returned or displayed at once.
- What happens if a user selects a Pessoa in the merged search-select field and then keeps typing?
  Typing again re-opens the narrowing list of matches; the form's actual selected value only
  changes when the user picks an option from that list, so an in-progress, not-yet-selected typed
  string is never submitted as if it were a chosen Pessoa.
- What happens to the previously-selected Pessoa when editing an existing Beneficiário whose
  linked Pessoa no longer matches typical search results (e.g., renamed)? The field continues to
  show that Pessoa as selected until the user deliberately searches for and picks a different one.
- What happens when a username search's typed text is shorter than the minimum length? No search
  runs yet, and the field shows its normal not-yet-searched state rather than a misleading "no
  results found" message.
- What happens when a username search matches more users than the capped result count? Only the
  capped number of matches is shown, and the person searching is expected to type more of the
  username to narrow the results further — the same way an overly broad Pessoa search already
  behaves.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Pessoa name filter (Pessoas list) MUST match any record whose name contains the
  typed text anywhere, case-insensitively (already the current behavior; this requirement locks it
  in as a guaranteed behavior of this feature, not an incidental one).
- **FR-002**: The Beneficiário list's Pessoa-name filter MUST match any record whose linked
  Pessoa's name contains the typed text anywhere, case-insensitively (already the current
  behavior; likewise locked in by this feature).
- **FR-003**: Username search (used both for the System Admin search screen and for adding a
  member to a Tenant) MUST match any user whose username contains the typed text anywhere,
  case-insensitively — replacing today's exact-match-only behavior — MUST NOT run until at least 2
  characters have been typed, and MUST return at most 20 matching users per search, to prevent a
  single broad search from listing the system's entire user base.
- **FR-004**: The Beneficiário form's Pessoa search MUST continue to match any Pessoa whose name
  contains the typed text anywhere, case-insensitively, consistent with FR-001.
- **FR-005**: The Beneficiário form's Pessoa "search box" and "select the result" MUST be
  presented as a single field: the user types into it to narrow the list of matching Pessoas and
  selects the desired one from that same field, rather than typing in one control and choosing
  from a visually separate one.
- **FR-006**: The combined Pessoa search-and-select field MUST require an actual selection from
  the matching list before the form can be submitted — free-typed text that does not correspond to
  a selected Pessoa MUST NOT be accepted as the form's Pessoa value.
- **FR-007**: When editing an existing Beneficiário, the combined Pessoa field MUST show the
  currently-linked Pessoa's name as the pre-selected value as soon as the form loads.
- **FR-008**: Non-text-search filters that select from a small, fixed set of values (e.g., a
  Beneficiário's Status or Tipo) are unaffected by this feature — they are not free-text searches
  and MUST continue to work exactly as they do today.
- **FR-009**: The Tenant member-management screen's "add member" search MUST let the person adding
  a member choose which specific matching user to add, using the same combined search-and-select
  presentation as FR-005, instead of the current behavior of silently adding the single matching
  user — since substring matching (FR-003) means more than one username can now match the same
  search text.

### Key Entities

- **Pessoa**: The global, shared-across-tenants person record searched and selected when creating
  or editing a Beneficiário; identified in search by name.
- **AppUser**: The account record searched by username both to grant/revoke System Admin status
  and to add as a member of a Tenant.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can find any Pessoa, Beneficiário, or account by typing any contiguous
  fragment of its name/username — not only its beginning — on the first attempt, across every
  search field in the application.
- **SC-002**: Selecting a Pessoa when creating or editing a Beneficiário takes exactly one field
  interaction (type, then pick from the same field) instead of two separate controls.
- **SC-003**: Adding a member to a Tenant by a partial username match never adds the wrong user —
  the person performing the action always confirms which specific matching user is added.
- **SC-004**: No existing search or filter capability regresses: every filter and search field that
  worked before this feature continues to return the same or a broader set of correct matches
  afterward.
- **SC-005**: A username search never returns more than 20 accounts at once and never runs on
  fewer than 2 typed characters — a single search can never list the system's entire user base.

## Assumptions

- "LIKE" in the feature request means substring (contains), case-insensitive matching — consistent
  with how the Pessoa and Beneficiário name filters already work today — not a literal SQL `LIKE`
  pattern exposed to end users, and not prefix-only ("starts with") matching.
- The Status and Tipo dropdowns on the Beneficiário list/form are fixed-choice selectors, not
  free-text searches, and are out of scope for the "LIKE" requirement.
- The login username field and the account-creation username field are identity inputs, not
  searches, and are out of scope for substring matching.
- Existing result-size limits (pagination on list filters, a capped result count on the Pessoa
  picker) are sufficient for the broader match sets substring search will produce, and are not
  changed by this feature.
- The combined search-and-select presentation applies to any field where a text search's results
  are used to choose one item from a list (the Beneficiário form's Pessoa field, and the Tenant
  "add member" username search); it does not apply to the System Admin search screen, whose
  results are shown as a table of independently actionable rows (grant/revoke buttons per row),
  not a single-value picker.
