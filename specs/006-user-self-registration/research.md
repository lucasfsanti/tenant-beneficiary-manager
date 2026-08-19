# Phase 0 Research: User Self-Registration (Bootstrap Entrypoint)

## §1. Preventing a double-bootstrap under concurrent registration

**Decision**: Wrap the "is this the very first account?" check and the resulting `INSERT` in a
single transaction guarded by a PostgreSQL transaction-scoped advisory lock
(`SELECT pg_advisory_xact_lock(<constant>)`), taken at the very start of `AuthService.register()`.
Only after acquiring the lock does the service check `app_user` row count; the role decision and
the `INSERT` both happen while still holding it. The lock auto-releases on commit or rollback —
no manual unlock, no extra cleanup code, consistent with how every other transactional method in
this codebase already relies on `@Transactional`'s boundary.

**Rationale**: The race described in spec Edge Cases ("two people submit at nearly the same
moment while the platform is still empty") is a classic check-then-act race on a *table-wide*
condition ("does any row exist at all"), not a race over any specific existing row. The
codebase's existing concurrency-safety pattern — `AppUserRepository.findAllSystemAdminsForUpdate()`
's `SELECT ... FOR UPDATE` used by grant/revoke (feature 003/004) — only works because it locks
*existing* rows; it cannot protect a table that currently has zero rows, since there's nothing to
lock via `FOR UPDATE` in that state. An advisory lock is PostgreSQL's standard mechanism for
exactly this situation: serializing access to a *logical* condition rather than to specific rows.
It requires no library (native to the already-used `postgres:16` image, callable via a native
query), and it is the narrowest possible fix — it only ever matters during the brief window while
the platform is still empty; every registration after the first never contends on it in any
observable way (the lock is still acquired for simplicity/uniformity, but never blocks anything
meaningful once row count is already ≥1, since no logic branches on contention past that point).

**Alternatives considered**:

- *`SERIALIZABLE` transaction isolation with retry-on-conflict*: PostgreSQL's textbook answer to
  write-skew races. Rejected because it would be the only `SERIALIZABLE` transaction anywhere in
  this codebase, requiring new retry-handling machinery (catching the serialization-failure
  exception, re-running the transaction) for a single narrow case — meaningfully more moving parts
  than one `pg_advisory_xact_lock` call, for the same outcome (Constitution Principle V).
- *`SELECT ... FOR UPDATE` on the whole `app_user` table*: Locking an empty result set locks
  nothing — this genuinely does not solve the problem, unlike the advisory lock, which encodes
  "only one bootstrap decision may happen at a time" directly, independent of how many rows
  currently exist.
- *A unique partial index / `CHECK` constraint expressing "at most one bootstrap"*: There's no
  column to build a meaningful uniqueness constraint on (every account is a normal `app_user` row
  with the same shape; "was this the platform's first account" is a fact about *when* it was
  created relative to others, not a value any single row's columns can encode uniquely).
- *Do nothing (accept the race)*: Rejected outright — spec Edge Cases explicitly requires that at
  most one concurrent submission may receive System Admin; silently allowing two would violate an
  explicit, testable requirement.

## §2. Schema impact

**Decision**: No new migration. `app_user.is_system_admin` (added by `003-role-system.sql`) and
the `user_tenant_membership` table (rows simply absent for a Normal account with no tenant) already
express every state this feature needs. `username` is already `UNIQUE NOT NULL` at the database
level (`001-schema.sql`), giving a second, DB-enforced backstop under the application-level
uniqueness pre-check.

**Rationale**: Verified by reading `001-schema.sql` and `003-role-system.sql` directly — both
columns/constraints this feature depends on already exist and already have the exact semantics
needed. Adding a migration where none is needed would violate Constitution Principle V (no
speculative changes).

**Alternatives considered**: None — this is a direct confirmation, not a genuine design choice.

## §3. Where the new endpoint lives

**Decision**: Add `POST /api/auth/register` to the existing `AuthController`/`AuthService` pair,
alongside `POST /api/auth/login` and `GET /api/me`.

**Rationale**: `AuthController` is already tagged "Autenticação: Login simplificado e perfil do
usuário autenticado" — registration is the same concern ("how does someone end up with a session
on this system") as login, just one step earlier in the flow. Keeping it in the same
controller/service pair avoids introducing a new component for what is, functionally, one more
way to end up authenticated. `AuthService` already owns `AppUserRepository` and
`PasswordEncoder` wiring, so `register()` needs no new collaborators beyond the advisory-lock
helper from §1.

**Alternatives considered**:

- *A new dedicated `RegistrationController`*: Rejected — would duplicate `AuthController`'s
  existing `AppUserRepository`/`PasswordEncoder` wiring for no separation-of-concerns benefit;
  this is a small, single-endpoint addition, not a distinct bounded concern (Constitution
  Principle V: no speculative structure).
- *Folding into `UserAdminController`*: Rejected — that controller is
  `@SecurityRequirement(name = "bearerAuth")`-protected and exclusively for already-authenticated
  admins granting/revoking standing on *existing* accounts; mixing in a public,
  unauthenticated-account-creation endpoint there would blur that controller's access-control
  boundary in a way that's easy to misread during review.

## §4. Request/response shape

**Decision**: `RegisterRequest(String username, String password)` — identical shape to the
existing `LoginRequest`, both fields `@NotBlank`. No role field is accepted from the client at
all (spec FR-011 — nothing the client supplies may influence the granted role, so the field
simply doesn't exist in the request). The endpoint returns `204 No Content` on success (mirroring
`UserAdminController`'s grant/revoke endpoints) rather than a token or profile — the account is
created but no session is established (spec Assumptions: the person is directed to the existing
login page afterward, not signed in automatically).

**Rationale**: Omitting a role field entirely (rather than accepting one and ignoring it) is a
stronger, more legible guarantee than "accept it but ignore it" — there is no client-controlled
input path into the role decision to audit or accidentally wire up later. `204 No Content` matches
the existing convention for "the request succeeded; there is nothing meaningful to return"
(`UserAdminController`), and keeps `AuthService.register()` from needing to duplicate
`login()`'s token-issuing logic for a case the spec says shouldn't auto-authenticate anyway.

**Alternatives considered**:

- *Accept an optional role field and silently ignore/reject it*: Rejected per FR-011's framing
  above — a field that exists but is ignored is a weaker guarantee and an easier place for a
  future change to accidentally wire client input into the role decision.
- *Auto-login on success (return a token, like `/auth/login` does)*: Rejected — contradicts spec
  Assumptions' explicit choice to route through the existing login page instead of establishing a
  session automatically.

## §5. Duplicate-username error shape

**Decision**: Reuse the existing `ConflictException` (already mapped by `ApiExceptionHandler` to
an RFC 7807 `409 Conflict` response), thrown from `AuthService.register()` after an explicit
pre-check (`AppUserRepository.findByUsername(...).isPresent()`), before attempting the `INSERT`.

**Rationale**: `ConflictException` already exists in `com.tbm.common.exception` specifically for
this class of "this would conflict with something that already exists" business rule, and is
already wired through the project's one standardized error-handling path (Constitution Principle
II). No new exception type is needed.

**Alternatives considered**: Catching the database's own unique-constraint violation instead of
pre-checking. Rejected as the primary mechanism (though the DB constraint still exists as a
backstop against a race between the pre-check and the insert) — translating a raw
constraint-violation exception into a clean RFC 7807 response is more roundabout than an explicit
pre-check that already matches how every other uniqueness rule in this codebase is enforced (e.g.,
matrícula uniqueness in the original CRUD feature).
