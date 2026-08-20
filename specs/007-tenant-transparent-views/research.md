# Research: Transparent Tenant Scoping via Database Views

## §1. Mechanism for propagating the active tenant into the database session

**Decision**: `SELECT set_config('app.tenant_id', :tenantId, true)` executed as the first
statement of every transaction that touches Beneficiário data, via a native query issued
through the JPA `EntityManager`. The third argument (`is_local = true`) makes this
transaction-scoped — equivalent to `SET LOCAL` — so PostgreSQL resets it automatically at
`COMMIT`/`ROLLBACK`.

**Rationale**: `is_local = true` is the only option that is safe under connection pooling
without extra cleanup code. A session-scoped `SET` (or `is_local = false`) would persist on the
pooled JDBC connection after the transaction ends and leak into whatever transaction/tenant
reuses that connection next — exactly the edge case the spec calls out ("tenant context must not
leak between transactions on a reused connection"). Transaction-scoped `set_config` closes that
gap structurally, with no reliance on remembering to call a "clear" method (unlike the existing
`TenantContext` `ThreadLocal`, which needs an explicit `finally { TenantContext.clear(); }` in
`TenantContextFilter`).

**Alternatives considered**:
- *`SET LOCAL app.tenant_id = '<uuid>'` as a literal string in the SQL.* Rejected — string
  interpolation of the tenant id into SQL text is unnecessary injection-shaped risk when
  `set_config(name, value, is_local)` accepts the value as a normal bind parameter.
  (`set_config`'s first argument, the setting *name*, is always the literal `'app.tenant_id'`,
  never client input, so there is no injection surface there either.)
- *Postgres Row-Level Security (`CREATE POLICY ... USING (tenant_id = current_setting(...))`)
  directly on the base table, entity mapped to the base table.* Rejected for this feature — RLS
  policies are enforced per-role and need `FORCE ROW LEVEL SECURITY` plus careful handling of the
  table owner (who bypasses RLS by default); the feature explicitly asks for **views** as the
  entity table, which is simpler to reason about with a single non-superuser application role
  and requires no role-security interaction. RLS remains a reasonable *future* hardening layer
  but is out of scope here.
- *A session-scoped variable set once per HTTP request (not per-transaction).* Rejected — a
  request can in principle span multiple transactions, and the spec ties the guarantee to "every
  transaction," not "every request"; transaction-scoping is both the safer and the literal
  reading of FR-003.

## §2. Making the view support INSERT/UPDATE/DELETE without exposing `tenant_id`

**Decision**: Keep the existing base table named `beneficiario` (no rename), and give its
`tenant_id` column a `DEFAULT NULLIF(current_setting('app.tenant_id', true), '')::uuid` (in
addition to its existing `NOT NULL REFERENCES tenant(id)`). Create a `vw_` -prefixed view over
it, per the project's naming convention for views:

```sql
CREATE VIEW vw_beneficiario AS
SELECT id, pessoa_id, matricula, tipo, status, data_adesao, created_at, updated_at
FROM beneficiario
WHERE tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid;
```

Both the `DEFAULT` and the view's `WHERE` clause wrap `current_setting('app.tenant_id', true)`
in `NULLIF(..., '')`, not the plain one-argument `current_setting('app.tenant_id')` used in an
earlier draft of this document. Discovered empirically while implementing T013's direct-SQL
test, in two stages:

1. PostgreSQL registers `app.tenant_id` as a session-level placeholder the first time it's ever
   set via `set_config` on a given connection, and from then on — for the rest of that pooled
   connection's lifetime — a transaction that never (re)sets it locally reads it back as `''`
   (empty string) rather than raising "unrecognized configuration parameter" (which is what
   happens only on a connection that has *never once* seen it set). Cast directly to `uuid`, `''`
   raises a raw `PSQLException` — the one-argument form is thus inconsistent across a
   connection's lifetime: an error once, then a cast failure forever after.
2. The two-argument `missing_ok` form alone does **not** fix this: `missing_ok = true` only
   suppresses the error for a parameter PostgreSQL has *never* seen — once the placeholder exists
   (state 1 above), the parameter is no longer "missing," so `current_setting(..., true)` returns
   its current value (`''`) exactly like the one-argument form would, and the `::uuid` cast still
   fails.

`NULLIF(expression, '')` folds both unset states — truly never referenced, and
previously-set-then-reset — down to `NULL` uniformly, regardless of connection history. A `NULL`
comparison in the view's `WHERE` clause is simply not-true (zero rows, no error), and a `NULL`
default on `INSERT` hits the column's own `NOT NULL` constraint (a clear, standard violation, not
a raw GUC error). This is what makes User Story 1 Acceptance Scenario 2 ("a transaction with no
tenant context returns no rows, not an error") literally true on a warmed-up connection pool —
which, in practice, is every connection after its first real request, in tests and in
production alike.

The `Beneficiario` JPA entity's `@Table` annotation changes from `@Table(name = "beneficiario")`
to `@Table(name = "vw_beneficiario")` — now pointing at the view instead of the base table — and
drops the `tenantId` field entirely. This is an internal persistence-mapping change only; the
REST contract (`/api/beneficiarios/**`) is unaffected (clarification session, 2026-08-20).

**Rationale**: PostgreSQL automatically supports `INSERT`/`UPDATE`/`DELETE` through a "simple"
view (single base relation, no `DISTINCT`/`GROUP BY`/set operations) even when some base-table
columns are omitted from the view's column list, *provided* an omitted `NOT NULL` column has a
`DEFAULT`. Since Hibernate's generated `INSERT` never mentions `tenant_id` (the entity has no
such field), Postgres falls back to the base table's `DEFAULT` expression — which reads the same
session variable the view's `WHERE` clause uses — so a row is transparently stamped with the
active tenant on creation with no code change needed at the entity/repository level beyond
removing the field. `UPDATE`/`DELETE` through the view are automatically restricted to rows
matching the `WHERE` clause, so they cannot touch another tenant's row even in principle — this
is what makes User Story 1 ("filtering happens even without an app-code tenant filter") literally
true for writes, not just reads.

**Alternatives considered**:
- *`INSTEAD OF` triggers on the view.* Rejected as unnecessary machinery — the base-table
  `DEFAULT` trick achieves the same result (transparent tenant stamping on insert) with a plain
  updatable view, which is simpler and keeps the view definition itself trivial to audit.
- *Rename the base table to something else (e.g. `beneficiario_data`) and give the view the
  historically-used bare name `beneficiario`.* This was the original decision in this document,
  superseded 2026-08-20: the user requested the project's `vw_` prefix convention for views
  instead, with the base table keeping its existing, unrenamed name. That reading has an added
  benefit over the original — it needs no `RENAME TABLE` at all (see §5), since the base table
  never changes name.

## §3. Where the per-transaction `set_config` call is triggered from

**Decision**: A small `@Component` `TenantSessionContext` in `com.tbm.security`, with one method
`apply(UUID tenantId)` that runs the native `set_config` query via the injected
`EntityManager`. `BeneficiarioService`'s existing `activeTenantId()` private helper (currently:
read `TenantContext.get()`, throw if null) is extended to also call
`tenantSessionContext.apply(tenantId)` before returning — so every one of the five existing call
sites (`list`, `get`/`findOrThrow`, `create`, `update`, `delete`) gets the new behavior for free,
with no per-method code duplicated.

**Rationale**: `BeneficiarioService` is already the single place (per Constitution Principle I
and this codebase's existing convention, see `BeneficiarioRepository`'s Javadoc) that reads the
resolved tenant before touching the repository. Extending that one existing chokepoint is the
smallest change that guarantees the new call cannot be forgotten on any path, and avoids
introducing a generic cross-cutting mechanism (AOP aspect, Hibernate `Interceptor`) for what is,
today, a single entity accessed from a single service — consistent with Constitution Principle V
(no speculative abstraction ahead of actual need).

**Alternatives considered**:
- *A Spring AOP `@Around` advice keyed on `@Transactional` methods.* Rejected — would need
  careful advisor ordering to run *inside* Spring's own transactional advice (so the native query
  executes within the already-started transaction, not before/outside it), adding indirection for
  a codebase with exactly one tenant-scoped entity today. Revisit if/when a second tenant-scoped
  entity is added and the per-service duplication becomes real, not hypothetical.
- *A Hibernate `StatementInspector`/`SessionFactory` interceptor.* Rejected as a heavier,
  framework-level hook for the same single-call-site problem.

## §4. Fail-closed behavior when establishing the tenant context fails (FR-003)

**Decision**: No special handling — `TenantSessionContext.apply()` lets any exception from the
native `set_config` query propagate unchanged. Because it is always invoked from inside an
already-`@Transactional` `BeneficiarioService` method, an uncaught `RuntimeException` triggers
Spring's normal rollback-on-unchecked-exception behavior, and `ApiExceptionHandler`'s existing
catch-all `handleUnexpected` maps it to a `500` RFC 7807 `ProblemDetail` — the same shape every
other unexpected failure already produces.

**Rationale**: This is the literal "abort the transaction and return an error" behavior the
clarification session settled on, achieved with zero new error-handling code — Spring/JPA's
default failure mode for an exception mid-transaction already is "roll back," so satisfying
FR-003 is a matter of *not* swallowing the exception, not adding new logic.

**Alternatives considered**:
- *Catch and wrap in a dedicated `TenantContextException` with a specific `ProblemDetail`
  title/type.* Considered but not required by any FR/SC — the spec asks for "a clear, actionable
  error," which the existing generic 500 handling already provides; a dedicated exception type
  can be added later if a distinct client-facing error code becomes a real requirement.

## §5. Migrating existing data without loss (FR-010)

**Decision**: One new, appended Liquibase changeset (next sequential file,
`006-tenant-view-and-audit-log.sql`) that runs, in order: `ALTER TABLE beneficiario ALTER COLUMN
tenant_id SET DEFAULT current_setting('app.tenant_id')::uuid`, then `CREATE VIEW vw_beneficiario
AS ...` (§2), then the new audit-log table (§6).

**Rationale**: With the base table keeping its existing name (§2, superseded 2026-08-20), this
migration needs no `RENAME TABLE` step at all — it only adds a column default and creates a new
view, neither of which touches or copies a single existing row. This is even more directly
loss-proof than the originally-considered rename-based approach, satisfying FR-010/SC-003 with
the smallest possible footprint. It follows the same append-only changelog convention already
established by specs 002/003/004/005 (never editing `001-schema.sql` in place).

**Alternatives considered**:
- *Rename the base table and create a new table, copy rows, swap names.* Rejected — with the
  base table's name unchanged, there is nothing to rename or copy; adding a `DEFAULT` and a
  `CREATE VIEW` are both metadata-only operations.

## §6. Reconciling the one existing cross-tenant query that isn't request-scoped

**Decision**: `TenantService.delete(tenantId)` (`backend/src/main/java/com/tbm/tenant/
TenantService.java:66`) currently calls `beneficiarioRepository.existsByTenantId(tenantId)` to
block deleting a Tenant that still has Beneficiário records — for an *arbitrary* `tenantId` path
variable, not the caller's active-tenant-selector value. Since the view has no `tenant_id`
column, this becomes: call `tenantSessionContext.apply(tenantId)` (the same helper from §3) for
the target tenant, then call the repository's inherited `count()` (backed by Spring Data,
querying `vw_beneficiario` now that the entity is remapped there) and check it's greater than
zero — no new repository method needed.

**Rationale**: Reuses the exact same view + session-variable enforcement mechanism for this
System-Admin-only cross-tenant check, rather than adding a second, parallel data-access path
that queries the base `beneficiario` table directly and would need to independently re-implement
(or bypass) the tenant-scoping guarantee. This keeps "the view is the only way `Beneficiario`
data is read or written" true without exception.

**Alternatives considered**:
- *Add a `tenantId`-parameterized native query against the base `beneficiario` table directly for
  this one call site.* Rejected — reintroduces exactly the kind of "one more repository method
  that must remember to filter correctly" risk this feature exists to eliminate, for one call
  site that doesn't need it.

**Note on scope**: This is a plumbing necessity, not a new instance of FR-013's audit-logging
requirement — `TenantService.delete()` is an existing `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`
administrative operation on Tenant management, explicitly out of this feature's "transparency"
scope per the spec's Assumptions (administrative/tenant-management endpoints keep explicit
tenant identifiers). FR-013 is scoped to the Beneficiário cross-tenant *viewing* path described
in User Story 3.

## §7. Recording System Admin cross-tenant access (FR-013)

**Decision**: A new table `tenant_access_audit_log` (`id UUID PK`, `admin_user_id UUID NOT NULL
REFERENCES app_user(id)`, `target_tenant_id UUID NOT NULL REFERENCES tenant(id)`, `accessed_at
TIMESTAMPTZ NOT NULL DEFAULT now()`), a matching `TenantAccessAuditLog` JPA entity + plain
`JpaRepository`, and one new call in `TenantContextFilter`: at the exact point it already
determines `isSystemAdmin && !membershipRepository.existsByUser_IdAndTenant_Id(...)` (i.e., the
bypass condition), save one audit row before calling `filterChain.doFilter(...)`.

**Rationale**: `TenantContextFilter` is already the single centralized place (per its own
Javadoc) where the bypass decision is made — it is the natural, and only, place that has both
the acting admin's id and the target tenant id at the moment the bypass is granted, with no
duplicated logic needed elsewhere. Logging at grant-time (rather than after the downstream
request completes) means the record reflects "the admin exercised the cross-tenant capability,"
matching User Story 3 Acceptance Scenario 3 — a subsequent 404 for a not-found Beneficiário id
is still a legitimate, correctly-audited use of the bypass, not a reason to suppress the log
entry.

**Alternatives considered**:
- *Log asynchronously / to an external log aggregator instead of a DB table.* Rejected as beyond
  this feature's scope — the constitution's zero-touch/simplicity principles favor the same
  PostgreSQL-backed, Liquibase-versioned storage already used for everything else in this
  project; nothing in the spec calls for external log infrastructure.
- *Log inside `BeneficiarioService` instead of the filter.* Rejected — the filter is the only
  place that actually evaluates the bypass condition today; duplicating that evaluation in the
  service layer would create two sources of truth for "was this a bypass access."
