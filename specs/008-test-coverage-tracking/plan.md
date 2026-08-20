# Implementation Plan: Test Coverage Tracking

**Branch**: `008-test-coverage-tracking` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-test-coverage-tracking/spec.md`

## Summary

Wire up code-coverage reporting into both stacks' existing local test commands (`mvn test`,
`npm test`) — JaCoCo for the backend, `@vitest/coverage-v8` for the frontend — as a visible
report only, never a build-failing gate, and no CI. Backend investigation (a live JaCoCo run
against the current suite, 127/127 passing) found the actual remaining gap is tiny and fully
enumerated: 4 missed lines / 3 missed branches across 5 classes, closeable entirely by adding
tests — zero production-code changes needed — plus one narrow, justified exclusion (the
`main()` bootstrap method). Frontend has no coverage tooling today and only 4 of 14 Vue files
have any test; closing that gap is the larger share of this feature's work, with `main.js`
(framework bootstrap, mirrors the backend's `main()`) as the one analogous exclusion.

## Technical Context

**Language/Version**: Java 21 (backend, unchanged), JavaScript/Vue 3 (frontend, unchanged)

**Primary Dependencies**: `org.jacoco:jacoco-maven-plugin` (backend, new); `@vitest/coverage-v8`
(frontend, new) — both are each ecosystem's standard, officially-supported coverage tool; no
other new dependency, no new test framework (JUnit 5/Spring Boot Test and Vitest/@vue/test-utils
stay exactly as they are)

**Storage**: N/A — this feature has no data model and touches no persisted entity

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + `@vue/test-utils`
(frontend) — this feature adds coverage instrumentation/reporting to these existing runners, not
a replacement for them

**Target Platform**: Same as today (local developer machines; Docker only for the
Testcontainers-backed Postgres backend tests already require) — no CI runner, since none exists
(per Clarifications)

**Project Type**: Web application (`backend/` + `frontend/`), structure unchanged

**Performance Goals**: N/A — coverage instrumentation has each tool's normal, small runtime
overhead; this feature does not set a performance target

**Constraints**: Neither `mvn test` nor `npm test` may change pass/fail outcome because of
coverage (FR-004); both reports must be viewable locally with no network access or paid service
(FR-003); no CI pipeline or run-on-push mechanism (FR-009)

**Scale/Scope**: Backend: 51 main classes, current measured coverage 99.28% line / 96.3%
branch — enumerated gap is 4 lines / 3 branches across 5 classes (research.md). Frontend: 14 Vue
files (4 already tested, 10 with zero tests) plus 4 Pinia stores and 5 API-service modules of
currently unmeasured coverage (no tooling exists yet to measure them)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Multitenant Data Isolation** — N/A/PASS. This feature is test infrastructure; it does not
  touch any Beneficiário read/write path, tenant-resolution code, or persisted data.
- **II. Data Integrity & Explicit Validation** — N/A/PASS. No data model, no validation rule
  changes.
- **III. API Contract Documentation** — N/A/PASS. No endpoint is added, removed, or changed;
  `contracts/` is intentionally omitted for this feature (see Project Structure below).
- **IV. Reproducible, Zero-Touch Environment** — PASS. Coverage reporting rides on the exact same
  local commands already documented (`mvn test`, `npm test`); `docker-compose up` and the running
  application are completely unaffected — coverage is a dev-time-only concern.
- **V. Simplicity & Justified Technology Choices** — PASS, and the principle most directly
  exercised here: JaCoCo and `@vitest/coverage-v8` are each ecosystem's zero-extra-infrastructure
  default (no new servers, no SaaS coverage service, no CI runner). The Clarifications-driven
  decisions — report-only (no build-failing gate), no CI — keep this addition proportionate to
  what a small project needs, per Principle V's "do not over-build."

No violations to justify; Complexity Tracking table is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/008-test-coverage-tracking/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

`data-model.md` and `contracts/` are intentionally omitted: this feature introduces no entity,
relationship, or API contract — only test-coverage tooling and new tests.

### Source Code (repository root)

```text
backend/
├── pom.xml                                  # + jacoco-maven-plugin (prepare-agent + report
│                                             #   bound to the `test` phase; main() excluded)
└── src/test/java/com/tbm/
    ├── integration/TenantCrudTest.java       # + "get/update/delete unknown tenant -> 404"
    ├── integration/MembershipManagementTest.java  # + "addMember to unknown tenant -> 404"
    ├── integration/SystemAdminBeneficiarioAccessTest.java  # + "admin who is also a genuine
    │                                             #   member produces no audit row" case
    ├── security/TenantContextFilterTest.java # + assertion on the saved audit row's getId()
    └── security/TenantAuthorizationTest.java # NEW: null / non-JwtPrincipal authentication cases

frontend/
├── package.json                             # + @vitest/coverage-v8 devDependency
├── vite.config.js                           # + test.coverage block (provider: v8, enabled:
│                                             #   true, main.js excluded)
└── tests/unit/
    ├── App.spec.js                          # NEW
    ├── ActiveTenantBadge.spec.js             # NEW
    ├── ErrorBanner.spec.js                   # NEW
    ├── PaginationControl.spec.js             # NEW
    ├── BeneficiarioListView.spec.js          # NEW
    ├── BeneficiarioFormView.spec.js          # NEW
    ├── PessoaListView.spec.js                # NEW
    ├── PessoaFormView.spec.js                # NEW
    ├── LoginView.spec.js                     # NEW
    ├── SystemAdminsView.spec.js              # NEW
    └── (stores/services)                     # NEW tests as needed once real numbers are known
                                                #   (exact set determined after coverage tooling
                                                #   is installed — see research.md)
```

**Structure Decision**: Existing web-application layout (`backend/` Spring Boot API, `frontend/`
Vue 3 SPA) is unchanged. All backend work is new/extended tests plus a `pom.xml` plugin addition
— zero production-code changes. All frontend work is a new devDependency, a `vite.config.js`
addition, and new test files for the currently-untested components/views/stores/services.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
