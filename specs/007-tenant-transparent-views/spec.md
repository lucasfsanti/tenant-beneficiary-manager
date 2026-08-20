# Feature Specification: Transparent Tenant Scoping via Database Views

**Feature Branch**: `007-tenant-transparent-views`

**Created**: 2026-08-20

**Status**: Draft

**Input**: User description: "Make the tenantId transparent in the API, getting from the JWT token. Use views in the database as the Entity table, omitting the tenantId. The tenantId should be set in the database at every transaction and used to filter the tenant directly in the database"

## Clarifications

### Session 2026-08-20

- Q: The current JWT carries a list of a user's tenant memberships, not a single active tenant. When a user belongs to more than one tenant, how should the system determine which tenant a request is "for" — without the client supplying a tenant identifier? → A: Keep the existing client-sent active-tenant selector, validated against the JWT's tenant memberships (unchanged resolution mechanism); "transparent" means the tenant identifier is dropped from the entity model and API payloads, and enforced at the database layer.
- Q: When a System Admin uses the explicit cross-tenant path to view a tenant they don't belong to, should that access be recorded so it can be audited later? → A: Yes — every cross-tenant access by a System Admin must be logged with actor, timestamp, and target tenant.
- Q: If the system fails to establish the tenant context for a database transaction (e.g., an error while setting it), what should happen to that transaction? → A: Abort the transaction and return an error — no tenant-scoped query may run without a confirmed tenant context.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tenant isolation is enforced by the database itself (Priority: P1)

The organization operating the system wants tenant isolation to hold even if application-level filtering code has a bug or is bypassed, because the previous approach relied entirely on every query remembering to filter by tenant. The active tenant continues to be resolved the way it is today (an active-tenant selector on the request, validated against the authenticated user's tenant memberships) — what changes is that filtering is enforced by the database itself once that tenant is resolved, not only by application query-building code.

**Why this priority**: This is the durability/defense-in-depth goal behind "use views... filter the tenant directly in the database," and it's the reason for the architecture change. It's the core of the request.

**Independent Test**: Inspect the tenant-scoped data path end-to-end and confirm that tenant filtering happens as part of the database access layer (not only in application query-building code), such that a transaction without a properly established tenant context cannot return any tenant-scoped rows.

**Acceptance Scenarios**:

1. **Given** a database transaction with the active tenant context established, **When** a query against tenant-scoped data runs even without an explicit tenant filter in the application code, **Then** only rows belonging to the active tenant are returned.
2. **Given** a database transaction that never had a tenant context established, **When** it attempts to read tenant-scoped data through the application's data-access layer, **Then** it receives no rows rather than unfiltered or another tenant's rows.
3. **Given** the application-level code path that would normally add a tenant filter, **When** that code path is bypassed or removed (simulated for testing), **Then** the database layer alone still prevents cross-tenant data from being returned.

---

### User Story 2 - API and entity model no longer carry a tenant identifier (Priority: P2)

Beneficiário API responses and the application's internal data model stop including a tenant identifier field/attribute. The tenant becomes an internal, database-enforced concern rather than something the API surfaces or the entity model stores per record.

**Why this priority**: Reduces the surface area for tenant-identifier misuse and simplifies the API contract, but is secondary to the database-enforcement guarantee itself (User Story 1) — it's a consequence of moving filtering into the view, not an independent mechanism.

**Independent Test**: Call Beneficiário endpoints (using the existing active-tenant selector and authentication, unchanged) and confirm response bodies contain no tenant identifier field; inspect the persistence model and confirm no tenant identifier attribute exists on the entity.

**Acceptance Scenarios**:

1. **Given** a valid, tenant-scoped request, **When** a Beneficiário record is returned, **Then** its representation does not include a tenant identifier field.
2. **Given** the entity/persistence layer for Beneficiário, **When** inspected, **Then** it has no tenant identifier attribute — tenant filtering happens entirely via the underlying database view.

---

### User Story 3 - Support staff can still investigate specific tenants (Priority: P3)

A System Admin (platform-wide role) occasionally needs to view or troubleshoot a specific tenant's Beneficiário data for support purposes, even though they are not a member of that tenant.

**Why this priority**: This preserves an existing capability (System Admin cross-tenant bypass) that a move to strict database-view-based filtering could otherwise silently remove. It's important but secondary to the core isolation and transparency goals.

**Independent Test**: Authenticate as a System Admin and use the explicit admin path to view a named tenant's Beneficiário data; confirm a non-admin user cannot do the same, and that the access is distinguishable from normal tenant-user traffic (e.g., for audit purposes).

**Acceptance Scenarios**:

1. **Given** a System Admin who is not a member of Tenant X, **When** they use the designated admin path and specify Tenant X, **Then** they can view Tenant X's Beneficiário data.
2. **Given** a non-admin tenant user, **When** they attempt to use the same admin path, **Then** the request is rejected.
3. **Given** a System Admin who successfully accesses Tenant X's data via the admin path, **When** the access completes, **Then** an audit record is created capturing who accessed it, when, and which tenant.

---

### Edge Cases

- What happens when an authenticated user has no tenant membership at all (e.g., a freshly self-registered account)? Tenant-scoped requests must be clearly rejected, not silently return an empty list.
- What happens when a client sends an active-tenant selector for a tenant the authenticated user is not a member of? It must be rejected outright — never silently substituted with a valid membership, and never trusted at the database layer without validation.
- What happens when a user's tenant membership is revoked after their token was issued but before it expires? The system's behavior should be consistent with its existing token-validity assumptions.
- What happens when a request is served by a pooled database connection previously used for a different tenant's transaction? The tenant context must not leak between transactions on a reused connection.
- What happens when the resolved tenant does not correspond to a real, active tenant (e.g., a deleted tenant)? The request must be rejected, not treated as tenant-less.
- What happens when establishing the tenant context in the database fails partway through (e.g., a database error while setting it)? The transaction must be aborted and an error returned — no tenant-scoped query may run without a confirmed tenant context.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST resolve the active tenant for every tenant-scoped operation using the existing active-tenant selector on the request, validated against the authenticated user's tenant memberships from their token — this resolution mechanism is unchanged by this feature.
- **FR-002**: Request and response payloads for tenant-scoped resources (e.g., Beneficiário) MUST NOT include a tenant identifier field.
- **FR-003**: The system MUST establish the resolved tenant as part of the database transaction/session context before any tenant-scoped query executes within that transaction, for every transaction that touches tenant-scoped data. If establishing that context fails for any reason, the system MUST abort the transaction and return an error rather than allow any query to execute without a confirmed tenant context.
- **FR-004**: Reads and writes of tenant-scoped data MUST be filtered by tenant at the database layer (e.g., via a filtering database view keyed off the transaction's tenant context), not solely by application query-building code.
- **FR-005**: The application's data model for tenant-scoped resources MUST be backed by the database-level filtering mechanism (not the raw underlying table) and MUST NOT expose a tenant identifier attribute at that layer.
- **FR-006**: A user MUST NOT be able to read, modify, or delete another tenant's data through the API by any means, including supplying an active-tenant selector value for a tenant they are not a member of, guessing another tenant's record identifiers, or tampering with tenant-related request data. Such attempts MUST be rejected before reaching tenant-scoped data, and MUST also be structurally prevented at the database layer as a second line of defense.
- **FR-007**: When an authenticated user has no resolvable active tenant, tenant-scoped requests MUST be rejected with a clear, actionable error rather than an empty or partial result.
- **FR-008**: System Admins MUST retain their existing explicit, distinct path to access a specifically named tenant's data for support purposes; this path MUST NOT be usable by non-admin users, and MUST continue to work under the new database-enforced model.
- **FR-009**: Entities that are not tenant-scoped today (e.g., Pessoa) MUST remain unaffected by this change and continue to be accessed without tenant filtering.
- **FR-010**: The migration from the current tenant-id-column-based filtering to the new database-view-based model MUST preserve all existing Beneficiário data without loss.
- **FR-011**: Tenant-isolation rejections MUST continue to return structured, actionable error responses consistent with the system's existing error-handling standard.
- **FR-012**: API documentation MUST be updated to reflect the removal of the tenant identifier field from affected response/request bodies (the active-tenant selector mechanism itself is unchanged and remains documented).
- **FR-013**: Every successful use of the System Admin cross-tenant access path MUST be recorded in an audit record capturing the acting admin, a timestamp, and the target tenant.

### Key Entities

- **Beneficiário**: The tenant-scoped business record. After this change, it is exposed to the application through a tenant-filtering database view rather than a raw table, and its API representation no longer carries a tenant identifier.
- **Tenant**: The organizational boundary that scopes Beneficiário data. Unchanged as a concept; remains explicit in administrative contexts (tenant/membership management).
- **Active Tenant Context**: The tenant identifier resolved for an authenticated request — via the existing active-tenant selector, validated against the user's token-listed memberships — and applied to the database session for the lifetime of a transaction. The resolution mechanism is unchanged; applying it to the database session is the new part this feature introduces.
- **Pessoa**: Global entity, explicitly unaffected — continues to be accessed without tenant filtering.
- **Tenant Access Audit Log**: A record of every System Admin access to a tenant they are not a member of — who accessed it, when, and which tenant — created automatically whenever the System Admin cross-tenant path (User Story 3) is used. Insert-only; not exposed through any API in this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of Beneficiário API responses omit any tenant identifier field, while all existing functionality (list/get/create/update/delete) continues to work using the existing active-tenant selector and authentication.
- **SC-002**: Querying the database directly, without going through the application layer at all, returns 0% cross-tenant records unless a tenant has been explicitly selected for that database session — the database layer alone prevents leakage, independent of any application code.
- **SC-003**: 100% of existing Beneficiário records are present and unchanged (by count and value) after migrating to the new data-access model.
- **SC-004**: 100% of requests supplying an active-tenant selector for a tenant the user is not a member of are rejected, across a representative set of test attempts.
- **SC-005**: System Admin cross-tenant access to a named tenant's data succeeds through the explicit admin path 100% of the time for admins, and is rejected 100% of the time for non-admins.
- **SC-006**: 100% of successful System Admin cross-tenant accesses produce a corresponding audit record identifying the admin, the time, and the target tenant.

## Assumptions

- Beneficiário is currently the only tenant-scoped entity; Pessoa, Tenant, and AppUser are unaffected by this change and are not migrated to the view-based model.
- The front-end continues to send the existing active-tenant selector (e.g., the current tenant header) unchanged; this feature does not remove or replace that resolution mechanism. What changes is how tenant filtering is enforced once the active tenant is resolved (database-level, via views, instead of only application-code WHERE clauses), and that the tenant identifier is dropped from response payloads and the entity model.
- Administrative endpoints that inherently operate across tenants (tenant management, membership management) are out of scope for this change — they continue to accept an explicit tenant identifier, since specifying the target tenant is the whole point of those operations, distinct from tenant-scoped business-data access.
- The database is PostgreSQL (per project constitution); the exact database-native mechanism used to set and apply the per-transaction tenant context is a technical decision left to the planning phase.
- Existing schema-migration conventions (append-only, versioned changelogs) are followed for the schema changes this feature requires.
- The existing token validity/expiry policy is assumed sufficient to bound the risk of a token reflecting a stale tenant membership; this feature does not introduce a new token-revocation mechanism.
