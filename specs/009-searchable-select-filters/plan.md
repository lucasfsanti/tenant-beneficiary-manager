# Implementation Plan: Searchable Select Filters

**Branch**: `009-searchable-select-filters` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-searchable-select-filters/spec.md`

## Summary

Make every free-text search substring/case-insensitive (already true for the Pessoa and
Beneficiário name filters; extends it to username search, which is exact-match-only today), and
replace the two-control "type to search, then pick from a separate dropdown" pattern with one
combined field wherever a search's results are used to choose a single item — the Beneficiário
form's Pessoa picker, and the Tenant "add member" username search (which currently has no
picker at all — it silently adds the sole exact match, a behavior substring matching would make
unsafe). Username search gains a server-enforced 2-character minimum and 20-result cap so
substring matching can't turn it into a full user-enumeration tool (spec Clarifications).

## Technical Context

**Language/Version**: Java 21 (backend, unchanged), JavaScript/Vue 3 (frontend, unchanged)

**Primary Dependencies**: Spring Data JPA (backend — one new derived/`@Query` repository method,
no new dependency); Vue 3 + Pinia (frontend — one new reusable component built with existing
primitives, no new dependency)

**Storage**: PostgreSQL, unchanged — no schema/migration change; this feature only changes query
predicates and result shaping on the existing `app_user` table

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + `@vue/test-utils`
(frontend) — unchanged

**Target Platform**: Same as today (local developer machines; `docker-compose up` for the full
stack) — no new infrastructure

**Project Type**: Web application (`backend/` + `frontend/`), structure unchanged

**Performance Goals**: N/A beyond today's — substring `LIKE` queries against `pessoa.nome` and
`app_user.username` (small local tables, no scale target stated) using the same query pattern
already in production for Pessoa/Beneficiário search

**Constraints**: Username search MUST NOT execute for fewer than 2 typed characters and MUST
return at most 20 matches (spec FR-003/SC-005) — enforced server-side, since the API is the actual
trust boundary (Constitution Principle II); the merged search-and-select field MUST NOT allow the
form to submit a value that wasn't chosen from the matching list (spec FR-006)

**Scale/Scope**: Two backend files touched (`AppUserRepository`, `UserController`, plus their
tests); one new frontend component plus two existing view files refactored to use it
(`BeneficiarioFormView.vue`, `TenantFormView.vue`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Multitenant Data Isolation** — N/A/PASS. `AppUser`/username search is not Beneficiário data
  and was never tenant-scoped (it's how a Tenant Admin finds *any* user, from any tenant, to
  invite) — this feature doesn't change that, and touches no Beneficiário read/write path.
- **II. Data Integrity & Explicit Validation** — PASS, and directly exercised: the 2-character
  minimum and 20-result cap on username search are server-enforced validation, not client-only —
  the front-end's own debounce/minimum-length check is a UX nicety, not the actual boundary,
  consistent with this principle's "the API is the actual trust boundary."
- **III. API Contract Documentation** — PASS, with one required update: `UserController`'s
  existing Swagger `@Tag` description ("busca... por username exato") becomes inaccurate once
  matching is substring-based and MUST be updated in the same change (spec FR-003; see
  contracts/README.md).
- **IV. Reproducible, Zero-Touch Environment** — PASS. No schema change, no new migration, no new
  seed data — `docker-compose up` behavior is unaffected.
- **V. Simplicity & Justified Technology Choices** — PASS, and the principle most directly
  exercised on the frontend: the combined search-and-select field is built as a small reusable Vue
  component from existing primitives (a text input plus a conditionally-rendered options list)
  rather than adding a new UI-library dependency (e.g., a combobox/autocomplete package) for what
  is, in this app, exactly two use sites — see research.md for the alternatives considered.

No violations to justify; Complexity Tracking table is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/009-searchable-select-filters/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
└── src/
    ├── main/java/com/tbm/user/
    │   ├── AppUserRepository.java     # + substring/capped username search query
    │   └── UserController.java        # exact-match -> substring search, min-length guard,
    │                                   #   Swagger description updated
    └── test/java/com/tbm/integration/
        └── UserLookupTest.java        # extended: substring match, below-minimum-length,
                                        #   over-cap, case-insensitivity

frontend/
└── src/
    ├── components/
    │   └── SearchableSelect.vue       # NEW reusable combined search-and-select field
    └── views/
        ├── BeneficiarioFormView.vue   # Pessoa search box + separate <select> -> one
        │                              #   SearchableSelect field
        └── TenantFormView.vue         # "add member" search -> SearchableSelect field,
                                        #   replaces silent auto-add-first-match
frontend/tests/unit/
├── SearchableSelect.spec.js           # NEW
├── BeneficiarioFormView.spec.js       # updated for the new Pessoa field structure
└── TenantFormView.spec.js             # updated for the new add-member field structure
```

**Structure Decision**: Existing web-application layout (`backend/` Spring Boot API, `frontend/`
Vue 3 SPA) is unchanged. Backend work is confined to the `user` package (repository query +
controller + its integration test). Frontend work adds one new reusable component and refactors
the two view files that need a combined search-and-select field; the Pessoa/Beneficiário name
filters and the `PessoaRepository`/`BeneficiarioRepository` queries already satisfy this feature's
substring-matching requirement and are not touched — only their regression coverage is confirmed
by `/speckit-tasks`.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
