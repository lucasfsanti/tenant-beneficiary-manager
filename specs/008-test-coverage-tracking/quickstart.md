# Quickstart: Validating Test Coverage Tracking

Prerequisites: a working Java 21 + Maven setup for the backend (per the project's existing local
test instructions) and Node.js for the frontend. No Docker/database is needed for the frontend
checks; the backend checks reuse the same `mvn test` command that already requires Docker for
Testcontainers.

## 1. Backend coverage report appears with no new command (FR-001, SC-001)

```bash
cd backend && mvn test
```

**Expected**: the command behaves exactly as it does today (same tests run, same pass/fail
outcome), and afterward `target/site/jacoco/index.html` exists and opens locally as a normal
file — no server, no network access needed.

## 2. Backend coverage is at target (User Story 1 / SC-003)

Open `target/site/jacoco/index.html` (or `target/site/jacoco/jacoco.csv`).

**Expected**: overall line and branch coverage is 100%, except for
`TenantBeneficiaryManagerApplication` — the one documented exclusion (research.md §2).

## 3. A coverage shortfall never fails the backend build (FR-004 / SC-006)

```bash
cd backend && mvn test; echo "exit code: $?"
```

**Expected**: exit code `0` whenever the tests themselves pass, regardless of the coverage
number — confirm this by checking that `pom.xml` has no `jacoco:check` execution bound
(`grep -n "jacoco:check\|<goal>check</goal>" backend/pom.xml` returns nothing).

## 4. Frontend coverage report appears with no new command (FR-002, SC-002)

```bash
cd frontend && npm test
```

**Expected**: the command behaves exactly as it does today, and a coverage report (per the
configured reporters, e.g. `frontend/coverage/index.html`) is produced automatically.

## 5. Frontend coverage is at target (User Story 2 / SC-004, SC-005)

Open the generated frontend coverage report.

**Expected**: overall coverage is 100% except for `src/main.js` — the one documented frontend
exclusion (research.md §6) — and every one of the previously-untested files (`App.vue`,
`ActiveTenantBadge.vue`, `ErrorBanner.vue`, `PaginationControl.vue`, `BeneficiarioListView.vue`,
`BeneficiarioFormView.vue`, `PessoaListView.vue`, `PessoaFormView.vue`, `LoginView.vue`,
`SystemAdminsView.vue`) now shows real coverage, not a blank/untested row.

## 6. A coverage shortfall never fails the frontend build (FR-004 / SC-006)

```bash
cd frontend && npm test; echo "exit code: $?"
```

**Expected**: exit code `0` whenever the tests themselves pass. Confirm no threshold is
configured: `grep -n "thresholds" frontend/vite.config.js` returns nothing (or an explicitly
empty/unset block).

## 7. Every exclusion is documented (User Story 3 / FR-006)

```bash
grep -n -B2 "TenantBeneficiaryManagerApplication" backend/pom.xml
grep -n -B2 "main.js" frontend/vite.config.js
```

**Expected**: both exclusions are accompanied by a comment explaining why, not a bare
file/class name with no context.

## 8. No CI was added (Clarifications)

```bash
ls .github/workflows/ 2>&1
```

**Expected**: `No such file or directory` — this feature does not introduce a CI pipeline.
