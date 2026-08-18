# Feature Specification: Pessoa & Beneficiário Multitenant Registry

**Feature Branch**: `001-pessoa-beneficiario-crud`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "define the Pessoa and Beneficiário CRUD features, the multitenant isolation behavior, filtering/pagination on Beneficiário listing, and the tenant-switch simulation in the front-end."

## Clarifications

### Session 2026-08-15

- Q: Once a user is signed in, can they freely switch to any of the pre-configured tenants at will, or is each signed-in user restricted to a specific tenant (or subset) they're associated with? → A: Each user account may belong to a defined subset of tenants; the switcher only offers those tenants.
- Q: What are the concrete set of values a Beneficiário's status can take? → A: Two values: Active, Inactive.
- Q: What is the definitive attribute list for Pessoa and Beneficiário? → A: Pessoa = nome (required), cpf (required, globally unique, format-validated), dataNascimento (optional), email (optional). Beneficiário = pessoaId (reference to an existing Pessoa), tenantId (implicit from the request context, never freely supplied by the client), matrícula (unique within the tenant), tipo (TITULAR or DEPENDENTE), status (ATIVO or INATIVO), dataAdesao (enrollment date). This supersedes the earlier "plan" attribute and the English `Active`/`Inactive` status labels from the prior clarification.
- Q: When a Pessoa deletion is blocked because a Beneficiário record still references it, should the explanation name which tenant(s) hold that reference, or just state generically that the Pessoa is still enrolled somewhere? → A: Generic message only — state that the Pessoa is still linked to one or more Beneficiário records, without naming which tenant(s), so the deletion-block explanation cannot be used to learn which of the platform's other tenants a person is enrolled in.
- Q: Should "tenant" be renamed to a Portuguese term, and how far should Portuguese naming extend into code/data model/UI? → A: Corrected after a follow-up: **"tenant"/"Tenant" is kept as-is** (not renamed to "Cliente" or any other term) — an earlier answer in this session renamed it to "Cliente" and propagated that into the design artifacts; that rename is retracted. The final, current scope is: entity and field names (e.g., Pessoa, Beneficiário, Tenant, nome, cpf, matrícula, tipo, status, dataAdesao) MUST be in Brazilian Portuguese where they already are (per the attribute-list clarification above), and all UI-facing text (labels, buttons, messages) MUST be in Portuguese. This does **not** require translating "Tenant" itself, nor does it require translating general code identifiers (class/package/component names) beyond what naturally follows from Portuguese entity/field names.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain the Global Pessoa Registry (Priority: P1)

A platform operator registers, reviews, updates, and removes Pessoa records — individuals identified by attributes such as name and CPF — in a single registry shared across the whole platform, independent of any client (tenant).

**Why this priority**: Pessoa is the foundation every Beneficiário record depends on. Without a working, validated Pessoa registry, no tenant can enroll anyone. It is also independently useful and demonstrable on its own.

**Independent Test**: Can be fully tested by creating, listing, editing, and deleting Pessoa records with no tenant context involved, and confirming the registry behaves the same regardless of which tenant is later active.

**Acceptance Scenarios**:

1. **Given** the Pessoa registry is empty, **When** the operator submits a new Pessoa with a valid name and CPF, **Then** the record is saved and appears in the Pessoa listing.
2. **Given** an existing Pessoa record, **When** the operator edits its name, **Then** the updated name is reflected immediately in the listing and detail view.
3. **Given** an existing Pessoa record, **When** the operator submits a CPF that is not a valid Brazilian CPF (bad format or bad check digit), **Then** the system rejects the submission with a specific, human-readable validation error and does not save the record.
4. **Given** an existing Pessoa record, **When** the operator attempts to register a second Pessoa with the same CPF, **Then** the system rejects the submission, explaining the CPF is already registered.
5. **Given** an existing Pessoa record that is not linked to any Beneficiário in any tenant, **When** the operator deletes it, **Then** the record is removed from the registry.

---

### User Story 2 - Manage Beneficiários Within the Active Tenant (Priority: P2)

A tenant operator, working inside one specific tenant, enrolls existing Pessoas as Beneficiários of that tenant, and reviews, updates, or removes those Beneficiário records — each carrying tenant-specific details such as enrollment number (matrícula), tipo (titular or dependente), status, and enrollment date (data de adesão).

**Why this priority**: This is the platform's core transaction — turning a global Pessoa into a tenant-specific Beneficiário — and delivers the primary value proposition once Pessoa records exist to link against.

**Independent Test**: Can be fully tested, given a small set of pre-existing Pessoa records and one active tenant, by creating a Beneficiário linked to a Pessoa, then editing, viewing, and removing it — confirming tenant-specific fields and validations behave correctly.

**Acceptance Scenarios**:

1. **Given** a Pessoa exists and a tenant is active, **When** the operator creates a Beneficiário linking that Pessoa with a matrícula, tipo, and status, **Then** the Beneficiário is saved under the active tenant and appears in that tenant's listing.
2. **Given** the operator is creating a Beneficiário, **When** they reference a Pessoa identifier that does not exist in the global registry, **Then** the system rejects the submission with a clear error instead of creating the record.
3. **Given** a Beneficiário already exists in the active tenant with matrícula "M001", **When** the operator tries to create another Beneficiário in the same tenant with matrícula "M001", **Then** the system rejects the submission as a duplicate matrícula for that tenant.
4. **Given** a Beneficiário with matrícula "M001" exists in tenant A, **When** an operator creates a Beneficiário with matrícula "M001" in tenant B, **Then** the creation succeeds, because matrícula uniqueness is scoped per tenant.
5. **Given** an existing Beneficiário in the active tenant, **When** the operator edits its tipo or status, **Then** the change is saved and reflected in the listing.
6. **Given** an existing Beneficiário in the active tenant, **When** the operator removes it, **Then** it no longer appears in that tenant's listing, and the linked Pessoa record remains untouched in the global registry.
7. **Given** required fields (linked Pessoa, matrícula, tipo, status) are missing from a submission, **When** the operator submits the form, **Then** the system rejects it and identifies each missing field.

---

### User Story 3 - Switch Active Tenant and Confirm Isolation (Priority: P3)

A user switches which tenant they are currently operating as, using a visible control, and the Beneficiário views immediately reflect only that tenant's data — with no data from other tenants ever visible, editable, or removable from the wrong tenant context.

**Why this priority**: Tenant isolation is the platform's defining guarantee. This story is what makes it observable and verifiable in the running system, but it depends on Beneficiário data existing (from Story 2) to be meaningfully tested.

**Independent Test**: Can be fully tested, given Beneficiário records already seeded in at least two different tenants, by switching the active tenant in the interface and confirming the listing, search, and direct-access views only ever show the newly active tenant's records.

**Acceptance Scenarios**:

1. **Given** the user is operating in Tenant A and viewing its Beneficiário listing, **When** the user switches the active tenant to Tenant B, **Then** the listing updates to show only Tenant B's Beneficiário records.
2. **Given** the user has switched to Tenant B, **When** the user switches back to Tenant A, **Then** Tenant A's Beneficiário records are exactly as they were left, unaffected by the visit to Tenant B.
3. **Given** the user is operating in Tenant A, **When** the user attempts to view, edit, or delete a specific Beneficiário record known to belong to Tenant B (e.g., by its identifier), **Then** the system denies the action and does not reveal Tenant B's record data.
4. **Given** the system has just started with no manual setup performed, **When** a pre-configured user with membership in two or more tenants opens the tenant switcher, **Then** exactly the tenants that user is associated with are offered as choices.
5. **Given** the user is authenticated, **When** the currently active tenant is displayed anywhere in the interface, **Then** it is unambiguous which tenant is active at all times.
6. **Given** a signed-in user is not associated with a given tenant, **When** the user attempts to select or otherwise activate that tenant (including by direct manipulation, e.g., a crafted request), **Then** the system denies the switch and the user's active tenant remains unchanged.

---

### User Story 4 - Search, Filter, and Paginate the Beneficiário Listing (Priority: P4)

A tenant operator, working with a potentially large list of Beneficiários in the active tenant, narrows the listing by the linked Pessoa's name and/or by status, and browses results a page at a time instead of scrolling through every record.

**Why this priority**: Valuable usability improvement once Beneficiário data exists in volume, but the platform is functional (Stories 1-3) without it — it refines rather than enables the core capability.

**Independent Test**: Can be fully tested, given a tenant with a sizeable number of Beneficiário records of varying names and statuses, by applying a name filter, a status filter, both together, and paging through results, confirming each returns the correct subset.

**Acceptance Scenarios**:

1. **Given** the active tenant has Beneficiários linked to Pessoas with different names, **When** the operator filters the listing by a partial name, **Then** only Beneficiários whose linked Pessoa name matches are shown.
2. **Given** the active tenant has Beneficiários with different statuses, **When** the operator filters by a specific status, **Then** only Beneficiários with that status are shown.
3. **Given** both a name and a status filter are applied, **When** the operator views the listing, **Then** only Beneficiários matching both conditions are shown.
4. **Given** the active tenant has more Beneficiário records than fit on one page, **When** the operator navigates to the next page, **Then** the next subset of records is shown without duplicates or omissions.
5. **Given** a filter matches no Beneficiário records in the active tenant, **When** the operator views the listing, **Then** the system shows a clear empty state rather than an error.

---

### Edge Cases

- What happens when a Beneficiário creation request references a Pessoa that exists in the global registry but has no relation to the active tenant yet? (Expected: allowed — that is precisely how a new tenant enrollment is created.)
- What happens when an operator tries to delete a Pessoa who is still linked to at least one Beneficiário in any tenant? (Expected: deletion is blocked with a generic explanation that does not name which tenant(s) reference it, to avoid leaking cross-tenant enrollment information to a user who may not be a member of those tenants.)
- How does the system respond when a filter or page request on the Beneficiário listing yields zero results?
- How does the system respond when a request for a specific Beneficiário record targets an identifier that belongs to a tenant other than the active one? (Expected: treated as not found for that tenant — no confirmation that the record exists elsewhere.)
- What happens when a pagination request asks for a page beyond the last available page? (Expected: an empty result set, not an error.)
- What happens when the same matrícula value is reused across two different tenants? (Expected: allowed, since matrícula uniqueness is scoped per tenant, not global.)
- What happens when required fields are missing or a CPF fails validation on either Pessoa or Beneficiário submission? (Expected: rejected with a specific, field-level error message.)
- What happens when a signed-in user tries to select or otherwise activate a tenant they are not associated with? (Expected: the switch is denied and the active tenant does not change, regardless of whether the attempt came from the visible switcher or a direct request.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow authorized users to create a Pessoa record with, at minimum, a name and a CPF, independent of any tenant.
- **FR-002**: System MUST validate that a Pessoa's CPF is a well-formed, check-digit-valid Brazilian CPF before accepting the record.
- **FR-003**: System MUST prevent two Pessoa records from sharing the same CPF.
- **FR-004**: System MUST allow authorized users to view, edit, and delete Pessoa records through the global registry.
- **FR-005**: System MUST block deletion of a Pessoa record that is still referenced by at least one Beneficiário record in any tenant, and MUST explain why the deletion was blocked using a generic message (e.g., "still linked to one or more Beneficiário records") that does NOT name or otherwise identify which tenant(s) hold the reference, since the requesting user may not be a member of those tenants.
- **FR-006**: System MUST allow an authorized user to create a Beneficiário record within the currently active tenant, linking it to an existing Pessoa record.
- **FR-007**: System MUST reject creation of a Beneficiário that references a Pessoa which does not exist in the global registry, with a clear, specific error message.
- **FR-008**: System MUST require each Beneficiário record to carry tenant-specific attributes, at minimum: the linked Pessoa, enrollment number (matrícula), tipo (one of exactly two values: TITULAR or DEPENDENTE), and status (one of exactly two values: ATIVO or INATIVO).
- **FR-009**: System MUST enforce that matrícula is unique within a tenant, while allowing the same matrícula value to be used independently by different tenants.
- **FR-010**: System MUST allow authorized users to view, edit, and delete Beneficiário records that belong to the currently active tenant.
- **FR-011**: System MUST prevent any user from viewing, editing, or deleting a Beneficiário record that does not belong to the currently active tenant, whether through listing, search, or direct access by identifier — with no indication that such a record exists elsewhere.
- **FR-012**: System MUST return clear, specific error messages for every rejected create/update operation, identifying which field or business rule caused the rejection (never a generic or unlabeled failure).
- **FR-013**: System MUST present the Beneficiário listing for the active tenant as a paginated list.
- **FR-014**: System MUST allow the Beneficiário listing to be filtered by the linked Pessoa's name, by status, or by both simultaneously.
- **FR-015**: System MUST provide a visible control that lets a user change the active tenant among the tenants that user is associated with, and MUST immediately re-scope all Beneficiário views and actions to the newly selected tenant.
- **FR-016**: System MUST always make it unambiguous to the user which tenant is currently active.
- **FR-017**: System MUST ship with at least two pre-configured tenants, at least one pre-configured user whose membership spans two or more of them, and at least one pre-configured user whose membership is restricted to exactly one tenant — all available immediately with no manual setup step required to create or select them. The single-tenant user makes the cross-tenant denial behavior (FR-021) demonstrable from seed data alone, not just the multi-tenant switching behavior.
- **FR-021**: System MUST restrict the tenant switcher to only the tenants the signed-in user is associated with, and MUST deny any attempt — through the switcher or by direct request — to activate a tenant the user is not associated with.
- **FR-018**: System MUST preserve each tenant's Beneficiário data independently across tenant switches, so that returning to a previously active tenant shows its data unchanged.
- **FR-019**: System MUST require all mandatory fields (nome and CPF for Pessoa; linked Pessoa, matrícula, tipo, and status for Beneficiário) to be present and valid before a record can be saved.
- **FR-020**: System MUST show a clear, non-error empty state when a filtered or paginated Beneficiário query returns no matching records.
- **FR-022**: System MAY optionally capture a Pessoa's dataNascimento and email; neither is required to create or maintain a Pessoa record.
- **FR-023**: System MUST allow an optional enrollment date (data de adesão) to be recorded on a Beneficiário record; if none is supplied, the system MUST default it to the record's creation date.
- **FR-024**: System MUST derive a Beneficiário's tenant strictly from the request's resolved active tenant context, and MUST NOT accept or trust a tenant identifier supplied directly on the create/update payload.
- **FR-025**: All user-facing UI text (labels, buttons, messages, validation errors shown to the user) MUST be in Brazilian Portuguese.

### Key Entities

- **Pessoa**: A global, platform-wide record representing an individual: nome and cpf (required, cpf globally unique and format-validated), plus optional dataNascimento and email. Exists independently of any tenant and is not scoped or filtered by tenant.
- **Beneficiário**: A tenant-scoped record that links one Pessoa to one tenant, carrying attributes meaningful only within that tenant's context (matrícula, tipo — TITULAR or DEPENDENTE, status — ATIVO or INATIVO, and an enrollment date). Its tenant is implicit from the request context and is never a freely client-supplied field. A single Pessoa may be linked to Beneficiário records in multiple tenants, but each Beneficiário record belongs to exactly one tenant and is only ever visible within it.
- **Tenant**: A client of the platform that defines a visibility boundary for Beneficiário records. Users operate within one active tenant at a time and can switch between the pre-configured tenants available to them. The name "Tenant" is kept as-is (not translated), per the 2026-08-15 clarification correction.
- **User**: A platform account used to sign in, associated with a defined subset of tenants (its membership). Determines which tenants appear in that user's tenant switcher and which tenant's Beneficiário data the user may ever access.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can register a new, valid Pessoa and see it appear in the global registry within a single submission, with zero manual refresh steps.
- **SC-002**: A user can create a Beneficiário linked to an existing Pessoa within the active tenant in a single form submission, and the record is immediately visible only in that tenant's listing.
- **SC-003**: 100% of attempts to view, edit, or delete a Beneficiário record from a tenant other than the active one are blocked, with zero cross-tenant Beneficiário records ever appearing in any listing, search result, or detail view during testing.
- **SC-004**: Switching the active tenant updates the visible Beneficiário data in a single action, with no leftover data from the previously active tenant remaining on screen.
- **SC-005**: Given a tenant with at least 50 Beneficiário records, a user can locate a specific record by name or status filter and view it within a single page of results, without manually scanning the unfiltered list.
- **SC-006**: 100% of invalid submissions (invalid CPF, missing required field, duplicate matrícula within a tenant, reference to a non-existent Pessoa) produce a specific, human-readable error identifying the problem, with zero generic or unexplained failures observed during testing.
- **SC-007**: A reviewer can open the running system with no manual setup, sign in as a pre-configured user with multi-tenant membership, and immediately see at least two tenants available for switching, each demonstrating independent Beneficiário data.
- **SC-008**: 100% of UI text visible to a user during a full walkthrough of the application (labels, buttons, messages, validation errors) is in Brazilian Portuguese, with zero untranslated English strings observed.
- **SC-009**: 100% of attempts to activate a tenant outside a signed-in user's membership are denied — whether via the visible switcher or a direct request — with the user's previously active tenant remaining in effect, observed during testing.

## Assumptions

- "Authorized user" means any user who has completed the platform's simplified sign-in. Pessoa operations (User Story 1) require only this sign-in and no active tenant selection, consistent with Pessoa being tenant-independent. Beneficiário operations and the tenant switcher additionally require the user to have an active tenant selected from among their tenant memberships. The feature does not introduce differentiated permission levels (e.g., read-only vs. full access) within a tenant beyond the membership check that gates Beneficiário access.
- Deleting a Beneficiário record is a hard removal from that tenant's data; it does not affect the linked Pessoa record or that Pessoa's Beneficiário records in other tenants.
- Deleting a Pessoa record is blocked while any Beneficiário record anywhere references it, favoring referential safety over silent cascading deletes.
- The tenant-switch control and the two-to-three pre-configured tenants exist purely to simulate and demonstrate multitenancy for review purposes; they are not a substitute for a full account-provisioning or tenant-management feature, which is out of scope here.
- Pagination defaults (page size, sort order) are a reasonable industry-standard choice (e.g., a fixed page size with newest or alphabetical ordering) left to downstream design, since the business requirement is that browsing works without loading the entire dataset at once.
- Pessoa's optional dataNascimento and email have no validation beyond basic well-formedness (a valid date; a syntactically valid email address) — no further business rule (e.g., minimum age, email verification) is implied by their being present.
- Beneficiário's dataAdesao, when omitted at creation, defaults to the record's creation date, consistent with how other optional date-like fields in the system behave without requiring the operator to supply one manually.
- The Brazilian Portuguese naming requirement applies to entity and field names (already Portuguese: Pessoa, Beneficiário, nome, cpf, matrícula, tipo, status, dataAdesao, etc.) and to all UI-facing text. It does not require renaming "Tenant," and does not mandate translating general code identifiers (class/package/component names) — those remain a downstream implementation choice, not a spec-level requirement.
