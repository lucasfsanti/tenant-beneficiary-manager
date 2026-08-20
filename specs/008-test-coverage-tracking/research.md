# Research: Test Coverage Tracking

## §1. Backend coverage tool and how it hooks into `mvn test`

**Decision**: Add `org.jacoco:jacoco-maven-plugin` (0.8.12, matching the version already
validated against this project's Java 21 / Spring Boot 3.3.4 toolchain during investigation) to
`backend/pom.xml`, with two executions: `prepare-agent` (default phase, before tests run) and
`report`, explicitly bound to the **`test`** phase rather than JaCoCo's own default (`verify`).

**Rationale**: The project's documented command is `mvn test` (README, `cd backend && mvn
test`) — `mvn test` does not run the `verify` phase, so binding `report` to its default phase
would silently produce no report when a developer runs the command they already know. Binding
`report` to `test` explicitly is the only way to satisfy FR-001 ("no additional manual setup
step beyond what is already documented for running the tests today") without changing what
command developers run.

**Alternatives considered**:
- *Leave `report` on its default `verify` binding and tell developers to run `mvn verify`.*
  Rejected — this fails FR-001 literally: it's a new command to learn and document, not the
  existing one.
- *A separate Maven profile (e.g., `mvn test -Pcoverage`).* Rejected — same problem: an opt-in
  flag is an "additional manual setup step," and the point of this feature is that the report is
  always there.

## §2. Excluding the backend's bootstrap entry point

**Decision**: Configure the JaCoCo `report` execution's `<configuration><excludes>` with
`com/tbm/TenantBeneficiaryManagerApplication.class`, with an inline XML comment stating the
reason (framework-invoked `main()`, never exercised by application-level tests).

**Rationale**: `TenantBeneficiaryManagerApplication.main()` only calls
`SpringApplication.run(...)` — it is invoked by the JVM at process start, not by any code this
project owns, and every `@SpringBootTest`-based integration test already boots the full
application context without ever calling `main()` itself (Spring Boot's test support starts the
context directly). Excluding it is FR-005's "narrow set of explicitly documented exclusions,"
with the rationale living directly in `pom.xml` next to the exclusion itself, satisfying FR-006.

**Alternatives considered**:
- *Write a test that calls `main()` directly.* Rejected — this either does nothing meaningful
  (calling a method that itself just delegates to Spring Boot's own, already-tested bootstrap
  code) or actually starts a second, redundant application context purely to paint a line green,
  which is precisely the "test written solely to raise the percentage" FR-008 prohibits.

## §3. The backend's actual remaining gap (measured, not estimated)

**Decision**: Run the JaCoCo report against the current suite (127/127 tests passing) before
writing any new test, and treat its exact output as the backend task list — no guessing at what
might be uncovered.

**Findings** (from `target/site/jacoco/jacoco.xml`, one line/branch each unless noted):

| Class | Line(s) | What's missing | Root cause | What closes it |
|---|---|---|---|---|
| `TenantService` | 86 | `findOrThrow`'s `orElseThrow(() -> new NotFoundException(...))` lambda never invoked | No existing test calls `get`/`update`/`delete` on a Tenant id that doesn't exist | A test: fetch/update/delete an unknown Tenant id, assert `404` |
| `MembershipService` | 46 | Same pattern, in `addMember`'s tenant lookup | No existing test calls `addMember` against an unknown Tenant id | A test: `POST` a member onto an unknown Tenant id, assert `404` |
| `TenantContextFilter` | 110 | One branch of the compound `isSystemAdmin && !isMember` condition | No existing test covers a System Admin who is *also* a genuine member of the target tenant (the seeded System Admin account has zero memberships) | A test: a System Admin with real membership in the target tenant produces **no** audit-log row (it's not a bypass) |
| `TenantAuthorization` | 24–26 | The `authentication == null` / not-a-`JwtPrincipal` defensive branch, entirely unexercised | `isTenantAdmin(UUID)` is only ever invoked through fully-authenticated `@PreAuthorize` calls in integration tests | A direct unit test constructing the bean and setting `SecurityContextHolder`'s authentication to `null` and to a non-`JwtPrincipal`, mirroring the existing precedent in `TenantContextFilterTest.passesThroughWhenThePrincipalIsNotAJwtPrincipal` |
| `TenantAccessAuditLog` | 32 | `getId()` never called | Existing assertions on a saved audit row check `getAdminUserId()`/`getTargetTenantId()` but not `getId()` | Add one assertion (`assertThat(saved.getId()).isNotNull()`) to the existing `TenantContextFilterTest` bypass test |

Total: 4 missed lines, 3 missed branches, all outside the one documented exclusion (§2). No
production code changes are required to reach 100% — every gap closes by adding or extending a
test that exercises real, already-intended behavior (satisfying FR-008).

**Rationale for measuring now rather than estimating**: A prior commit's message claimed "100%
branch / 99.7% line coverage" with no tooling ever committed to reproduce that number (§ project
history noted during spec investigation) — repeating that mistake (asserting a coverage state
without a report to back it up) is exactly what this feature exists to prevent.

## §4. Frontend coverage tool

**Decision**: `@vitest/coverage-v8` (V8's native coverage, Vitest's own documented default
pairing), added as a devDependency and enabled via `vite.config.js`'s existing `test` block.

**Rationale**: The frontend already runs on Vite + Vitest; `coverage-v8` needs no source
instrumentation step (V8 collects coverage natively at the engine level) and no Babel
configuration, making it the lowest-friction option that still produces accurate line and branch
numbers for a Vue 3 + `<script setup>` codebase.

**Alternatives considered**:
- *`@vitest/coverage-istanbul`.* Rejected — requires Babel-based source instrumentation
  (slower, an extra transform step) for branch-coverage precision that `coverage-v8` already
  provides well enough for this codebase's size; no concrete need justifies the extra
  configuration surface.

## §5. Making `npm test` produce the frontend report without a new command

**Decision**: Set `test.coverage.enabled: true` inside the existing `test` block of
`vite.config.js`, rather than requiring a `--coverage` flag or a new `npm run test:coverage`
script. `package.json`'s existing `"test": "vitest run"` script is left untouched.

**Rationale**: Mirrors §1's backend reasoning exactly — FR-002 requires the report from the
**existing** test command. `enabled: true` in config makes any invocation of `vitest run`
(including today's `npm test`) collect and emit coverage automatically, with zero change to how
developers already run the tests.

**Alternatives considered**:
- *Add a separate `npm run test:coverage` script, leave `npm test` as-is.* Rejected — same
  reasoning as backend §1: an extra command to remember is an "additional manual setup step,"
  which FR-002 rules out.

## §6. Excluding the frontend's bootstrap entry point

**Decision**: Exclude `src/main.js` from the frontend coverage configuration, with a comment
explaining why — the direct structural analog of §2 on the backend side.

**Rationale**: `main.js` only wires up the Vue app instance, Pinia, the router, and mounts to a
real DOM node (`app.mount('#app')`) — there is no `#app` element in the Vitest/jsdom test
environment, so importing this file at all would attempt a real mount and fail, not exercise
meaningful logic. Every piece of *actual* behavior `main.js` touches (the router, the auth
store's `fetchProfile`, `App.vue`'s own logic) is already independently testable and in scope
for its own tests. Note that `App.vue` itself is **not** excluded — unlike `main.js`, it has real
conditional rendering (role-based nav links) and a `handleLogout` handler, which are exactly the
kind of behavior this feature exists to cover (see plan.md's frontend test list).

**Alternatives considered**:
- *Test `main.js` by mocking `document.getElementById('app')` and asserting the app mounts.*
  Rejected for the same reason as excluding the backend's `main()`: it would only re-prove that
  Vue's own `mount()` works, not anything this project owns — a test written to move a number,
  not to catch a real regression.

## §7. What "report-only, no CI" means concretely

**Decision**: No `jacoco:check` execution is bound anywhere in `pom.xml` (JaCoCo only *reports*
by default — a `check` goal with rules is a separate, opt-in addition, so simply never adding it
guarantees `mvn test` cannot fail because of coverage). On the frontend, `coverage.thresholds` is
left unset in `vite.config.js`'s `test.coverage` block (Vitest only enforces thresholds if
explicitly configured). No `.github/workflows/` directory or other automation is added.

**Rationale**: Directly implements the Clarifications decisions (report-only enforcement, no CI)
with the simplest possible mechanism in each tool — omission, not a feature that has to be
disabled or configured around.
