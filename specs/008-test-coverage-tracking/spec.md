# Feature Specification: Test Coverage Tracking

**Feature Branch**: `008-test-coverage-tracking`

**Created**: 2026-08-20

**Status**: Draft

**Input**: User description: "Add a way to track the test coverage of the application, and if not at 100%, create those missing tests"

## Clarifications

### Session 2026-08-20

- Q: Investigation found the backend already has ~99% line / ~96% branch coverage (no tooling wired up to report it), while the frontend has zero coverage tooling and only 4 of 14 Vue files have any test at all. Should this feature cover both stacks, or just the backend? → A: Both backend and frontend.
- Q: Should running the test suite FAIL when coverage drops below the target (a hard local gate), or is a visible report enough for now? → A: Visible report only — the test commands must keep succeeding regardless of the coverage number.
- Q: Should adding a CI pipeline (GitHub Actions running tests on every push) be part of this feature, given the project's README already lists it as separate future work? → A: Out of scope — local tooling only, invoked via the existing local test commands.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Backend coverage is visible and complete (Priority: P1)

A developer runs the backend's existing test command and can immediately see exactly what
percentage of the backend code is covered by automated tests, broken down enough to spot any gap
— and today, running that same command closes the (small) remaining gap rather than leaving it
unnoticed.

**Why this priority**: The backend is already close to fully covered; this delivers the most
value for the least effort and proves the reporting mechanism works before the larger frontend
effort.

**Independent Test**: Run the backend's existing test command with no extra setup step, open the
resulting report, and confirm it shows a line and branch percentage per class, with the overall
number at 100% except for explicitly documented exclusions.

**Acceptance Scenarios**:

1. **Given** a clean checkout of the repository, **When** a developer runs the backend's existing
   test command, **Then** a coverage report is produced automatically, viewable locally, with no
   separate installation or configuration step.
2. **Given** the generated backend coverage report, **When** a developer inspects it, **Then**
   every class shows line and branch coverage, and the overall totals are 100% except for classes
   listed as documented exclusions.
3. **Given** a backend coverage shortfall exists, **When** the test command finishes, **Then** the
   command still reports success — the shortfall is visible in the report, not a build failure.

---

### User Story 2 - Frontend coverage is visible and complete (Priority: P2)

A developer runs the frontend's existing test command and can see exactly what percentage of the
front-end code is covered by automated tests — including the majority of components/views that
currently have no test at all — and running that command shows the gap closed.

**Why this priority**: This is the larger body of work (a coverage tool has to be introduced from
scratch, and most files currently have zero tests), so it follows the backend once the reporting
approach is proven.

**Independent Test**: Run the frontend's existing test command with no extra setup step, open the
resulting report, and confirm it shows a coverage percentage per file, with the overall number at
100% except for explicitly documented exclusions.

**Acceptance Scenarios**:

1. **Given** a clean checkout of the repository, **When** a developer runs the frontend's
   existing test command, **Then** a coverage report is produced automatically, viewable locally,
   with no separate installation or configuration step.
2. **Given** the generated frontend coverage report, **When** a developer inspects it, **Then**
   every source file shows a coverage percentage, and the overall totals are 100% except for
   files listed as documented exclusions.
3. **Given** a frontend coverage shortfall exists, **When** the test command finishes, **Then**
   the command still reports success — the shortfall is visible in the report, not a build
   failure.

---

### User Story 3 - Every exclusion is documented, not silent (Priority: P3)

A reviewer looking at either coverage report, or at the project's configuration, can see exactly
which code is excluded from the 100% target and why — never a gap that simply isn't mentioned
anywhere.

**Why this priority**: Without this, "100%" could be quietly gamed by excluding files with no
explanation, which would undermine the whole point of tracking coverage. It matters, but only
once there's a real report and a real target to make honest (User Stories 1 and 2).

**Independent Test**: List every exclusion configured for either coverage tool and confirm each
one has an accompanying written reason, findable without digging through commit history.

**Acceptance Scenarios**:

1. **Given** a piece of code is excluded from the 100% coverage target, **When** a reviewer looks
   at the project's coverage configuration or documentation, **Then** they find a written
   rationale for that specific exclusion.
2. **Given** the full set of documented exclusions, **When** a reviewer reviews them, **Then**
   each one is narrow (a specific method/branch/file, not a broad package) and explainable on its
   own merits (e.g., "framework-invoked entry point, never exercised by application tests").

---

### Edge Cases

- What happens to code that is structurally unreachable given the application's own guarantees
  (e.g., a defensive branch that can't be triggered through any legitimate flow)? It becomes a
  documented exclusion (User Story 3), not a contrived test that executes the line without
  asserting anything meaningful.
- What happens to the application's own bootstrap/entry-point code (e.g., the method that starts
  the backend process)? Treated as a reasonable default exclusion, since it is invoked by the
  framework/runtime rather than by application logic, and is documented like any other exclusion.
- What happens when a new file is added to the codebase after this feature ships, without a
  test? It shows up as a gap the next time the existing test command runs — no special handling
  is needed since coverage reporting is now part of the standard local workflow, not a one-time
  action.
- What happens to simple data-holder code that is only exercised incidentally by other tests
  (e.g., a request/response record)? No special exemption — if existing or new tests already
  construct and use it, it is already covered; nothing further is required.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Running the backend's existing test command MUST produce a coverage report (line
  and branch percentage, broken down per class) with no additional manual setup step beyond what
  is already documented for running the tests today.
- **FR-002**: Running the frontend's existing test command MUST produce a coverage report
  (statement/line and branch percentage, broken down per file) with no additional manual setup
  step beyond what is already documented for running the tests today.
- **FR-003**: Both coverage reports MUST be viewable locally (e.g., as an HTML report) without
  requiring network access or any paid/external service.
- **FR-004**: A coverage shortfall MUST be visible in the report but MUST NOT cause the backend or
  frontend test command to fail or report an error on account of the coverage number.
- **FR-005**: All backend and frontend application code MUST reach 100% line and branch (or
  statement/branch, for the frontend) coverage, except for a narrow set of explicitly documented
  exclusions.
- **FR-006**: Every exclusion from the 100% target MUST carry a written rationale that is visible
  in the project's configuration or documentation, not a silent, unexplained gap.
- **FR-007**: Every application file that currently has no automated test MUST gain one as part of
  reaching the coverage target, unless that file is itself a documented exclusion.
- **FR-008**: A test added to close a coverage gap MUST exercise and assert on real application
  behavior; it MUST NOT merely execute a line or branch without a meaningful assertion, solely to
  raise the reported percentage.
- **FR-009**: This feature MUST NOT introduce a CI pipeline or any mechanism that runs
  automatically on code push; both coverage reports remain invoked only via the existing local
  test commands.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After running the backend's existing test command, a developer can find the overall
  backend coverage percentage without installing or configuring anything beyond what running the
  tests already requires.
- **SC-002**: After running the frontend's existing test command, a developer can find the overall
  frontend coverage percentage without installing or configuring anything beyond what running the
  tests already requires.
- **SC-003**: 100% of backend application code is exercised by an automated test, with every
  exception explicitly documented and individually justified.
- **SC-004**: 100% of frontend application code is exercised by an automated test, with every
  exception explicitly documented and individually justified.
- **SC-005**: Every application file that had zero associated tests before this feature has
  meaningful test coverage afterward, except for documented exclusions.
- **SC-006**: The backend and frontend test commands' pass/fail outcome is unchanged by this
  feature — both keep succeeding under the same conditions as before, with a coverage report as
  an addition, not a new failure mode.

## Assumptions

- "100%" means line and branch coverage for the backend, and statement/line and branch coverage
  for the frontend, as measured by each stack's own test framework — not mutation-testing-level
  confidence or any other stronger notion of coverage.
- The backend's existing test suite is already close to this target; most backend work is closing
  a small, specific set of gaps rather than writing a new suite from scratch.
- The frontend has no coverage-reporting capability today; introducing one is treated as part of
  "a way to track" coverage, not a change of the frontend's existing test framework/runner itself.
- The exact coverage tool, report format, and configuration mechanism for each stack are technical
  decisions left to the planning phase; this spec only requires that a human-readable, locally
  viewable report exists for both, per FR-001–FR-003.
- Excluding the application's own bootstrap/entry-point method(s) from the 100% target is a
  reasonable, standard default, subject to FR-006's documentation requirement — it is not treated
  as a gap requiring a contrived test.
- No CI pipeline is introduced by this feature (see Clarifications); it remains a separately
  scoped, already-documented future item for this project.
