# Quickstart: Pessoa & Beneficiário Multitenant Registry

Validates the feature end-to-end against the acceptance scenarios in [spec.md](./spec.md), using
the data model in [data-model.md](./data-model.md) and the contract in
[contracts/openapi.yaml](./contracts/openapi.yaml).

## Prerequisites

- Docker and Docker Compose installed.
- No manual database setup, migration, or seeding — `docker-compose up` handles all of it
  (Constitution Principle IV).

## 1. Start the stack

```bash
docker-compose up
```

Expected: three healthy containers — PostgreSQL, backend (Spring Boot, Flyway migrations +
seed data applied on startup), frontend (built Vue 3 app served on its container port). No
additional command is required.

## 2. Explore the API contract

Open the backend's Swagger UI (springdoc-generated, per research.md §5) and confirm every
endpoint in `contracts/openapi.yaml` is present and documented from the running code.

## 3. Sign in and confirm tenant membership (validates FR-015, FR-016, FR-021, User Story 3)

1. Open the frontend in a browser.
2. Log in as the seeded multi-tenant demo user (see `V2__seed_demo_data.sql` for exact
   credentials).
3. Confirm the tenant switcher lists exactly that user's memberships (≥2 tenants) — not every
   tenant in the system.
4. Confirm the currently active tenant is unambiguously displayed.

## 4. Manage the global Pessoa registry (validates User Story 1)

1. Navigate to the Pessoa screen and create a new Pessoa with a valid name and CPF.
   Expected: appears immediately in the listing.
2. Attempt to create a second Pessoa with the same CPF. Expected: rejected with a specific
   "CPF already registered" error (RFC 7807 body), not a generic failure.
3. Attempt to create a Pessoa with an invalid CPF (bad check digit). Expected: rejected with a
   field-level validation error.
4. Edit the first Pessoa's nome. Expected: change reflected in the listing.

## 5. Manage Beneficiários in the active tenant (validates User Story 2)

1. With Tenant A active, create a Beneficiário linking the Pessoa created above, with a
   matrícula, tipo `TITULAR`, and status `ATIVO`. Expected: appears in Tenant A's listing.
2. Attempt to create a Beneficiário referencing a random, non-existent Pessoa id (e.g. via the
   API directly). Expected: 400 Problem Detail, not a 500.
3. Attempt to create a second Beneficiário in Tenant A with the same matrícula. Expected: 409
   Conflict.
4. Switch to Tenant B and create a Beneficiário with that same matrícula value. Expected:
   succeeds (matrícula uniqueness is per-tenant — data-model.md).
5. Edit the Tenant A Beneficiário's status to `INATIVO`. Expected: reflected in the listing.
6. Delete the Tenant B Beneficiário. Expected: removed from Tenant B's listing; the linked
   Pessoa remains in the global registry.

## 6. Confirm tenant isolation (validates User Story 3, SC-003, Constitution Principle I)

1. While Tenant A is active, note a Beneficiário id known to belong to Tenant B (from step 5.4
   above, or seed data).
2. Attempt `GET /api/beneficiarios/{thatId}` with `X-Tenant-Id` set to Tenant A. Expected: 404
   Not Found — no confirmation the record exists elsewhere.
3. Attempt the same request with `X-Tenant-Id` set to a tenant the signed-in user is **not** a
   member of at all. Expected: 403 Forbidden, before any Beneficiário lookup happens.
4. Switch back to Tenant B in the UI and confirm its Beneficiário listing is exactly as left in
   step 5, unaffected by the visit to Tenant A.

## 7. Filter and paginate (validates User Story 4, SC-005)

1. In a tenant with several Beneficiário records (seed data or ad hoc test data), filter the
   listing by a partial Pessoa nome. Expected: only matching rows shown.
2. Filter by status `ATIVO`. Expected: only active rows shown.
3. Combine both filters. Expected: intersection of both conditions.
4. With more records than one page holds, navigate to page 2. Expected: the next subset, no
   duplicates or omissions.
5. Apply a filter that matches nothing. Expected: a clear empty state, not an error.

## 8. Error format spot-check (validates Constitution Principle II)

Trigger at least one validation error (invalid CPF), one conflict (duplicate matrícula), and one
not-found (cross-tenant Beneficiário id) directly against the API and confirm each response body
is `application/problem+json` with `title`, `status`, and `detail` populated — never a bare 500
or an unstructured error string.

## 9. UI language spot-check (validates FR-025, SC-008)

Walk through every screen (login, Pessoa list/form, Beneficiário list/form, tenant switcher) and
confirm every label, button, and validation/error message shown to the user is in Brazilian
Portuguese, with no untranslated English strings.
