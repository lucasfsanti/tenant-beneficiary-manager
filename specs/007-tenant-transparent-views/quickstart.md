# Quickstart: Validating Transparent Tenant Scoping

Prerequisites: `docker-compose up` running the full stack (demo profile, per existing project
README), or `AbstractIntegrationTest`'s Testcontainers-backed suite. Seeded demo users (see
`AbstractIntegrationTest`): `User 1 - NORMAL` (member of Tenant Alfa `11111111-…` and Tenant
Beta `22222222-…`), `User 3 - ADMIN` (System Admin, no tenant memberships).

## 1. API responses no longer carry a tenant identifier (User Story 2 / SC-001)

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"User 1 - NORMAL","password":"demo123"}' | jq -r .token)

curl -s localhost:8080/api/beneficiarios \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" | jq '.content[0]'
```

**Expected**: the returned object has no `tenantId` field (compare against
`contracts/beneficiario-api.md`'s "After" shape).

## 2. Cross-tenant access is rejected even by record ID (User Story 1 / FR-006 / SC-004)

Note a Beneficiário id under Tenant Alfa, then request it while the active-tenant selector is
Tenant Beta:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  localhost:8080/api/beneficiarios/<alfa-beneficiario-id> \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 22222222-2222-2222-2222-222222222222"
```

**Expected**: `404` (the view returns no row for that id under Tenant Beta's session context —
same behavior as "record doesn't exist," per Edge Cases).

## 3. Database-level enforcement holds without application filtering (User Story 1 / SC-002)

Connect directly to PostgreSQL (`docker-compose exec db psql -U tbm -d tbm`) and confirm the
`vw_beneficiario` view enforces scoping independent of any Java code, while the base
`beneficiario` table is unfiltered (proving it's genuinely the view doing the work, not some
trigger on the base table):

```sql
-- No tenant context set for this session:
SELECT count(*) FROM vw_beneficiario;  -- expect 0, not an error and not all rows
SELECT count(*) FROM beneficiario;     -- expect ALL rows across every tenant — this is the raw base table

BEGIN;
SELECT set_config('app.tenant_id', '11111111-1111-1111-1111-111111111111', true);
SELECT count(*) FROM vw_beneficiario;  -- expect only Tenant Alfa's rows
COMMIT;

SELECT count(*) FROM vw_beneficiario;  -- expect 0 again — set_config's is_local=true reset at COMMIT
```

Also confirm the view hides the column entirely:

```sql
\d vw_beneficiario  -- tenant_id must NOT appear in the view's column list
\d beneficiario     -- tenant_id (with its new DEFAULT) still on the base table
```

## 4. System Admin cross-tenant access still works, and is audited (User Story 3 / FR-013 / SC-005 / SC-006)

```bash
ADMIN_TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"User 3 - ADMIN","password":"demo123"}' | jq -r .token)

curl -s localhost:8080/api/beneficiarios \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "X-Tenant-Id: 11111111-1111-1111-1111-111111111111" | jq '.content | length'
```

**Expected**: succeeds (`200`) even though `User 3 - ADMIN` has no membership in Tenant Alfa.
Then, via `psql`:

```sql
SELECT admin_user_id, target_tenant_id, accessed_at FROM tenant_access_audit_log
ORDER BY accessed_at DESC LIMIT 1;
```

**Expected**: one new row, `target_tenant_id = 11111111-…`, `admin_user_id` matching `User 3 -
ADMIN`'s id.

## 5. A non-admin cannot use the same path (User Story 3, acceptance scenario 2)

Repeat step 4's request with `$TOKEN` (the non-admin `User 1 - NORMAL`) against a tenant they do
not belong to — expect `403`, and no new `tenant_access_audit_log` row.

## 6. Existing data survived the migration untouched (FR-010 / SC-003)

Compare a `SELECT count(*) FROM beneficiario` (post-migration — the base table is never renamed
or copied by this feature) against the pre-migration row count recorded before running the new
changeset — they must match, and a spot check of a few known seeded rows (`id`, `matricula`,
`pessoa_id`) must be byte-for-byte identical.
