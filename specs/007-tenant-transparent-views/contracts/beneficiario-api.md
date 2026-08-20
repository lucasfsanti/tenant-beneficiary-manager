# Contract Delta: `/api/beneficiarios/**`

This feature changes the *response* shape of the existing Beneficiário endpoints. It does not
add, remove, or change any endpoint's method, path, status codes, or request shape — the
`X-Tenant-Id` header remains required exactly as it is today (FR-001).

Full contract (methods, paths, status codes, request bodies, pagination) is unchanged from what
`BeneficiarioController` already exposes via OpenAPI/springdoc at `/v3/api-docs` and
`/swagger-ui.html`; only the item below changed there, automatically, from the DTO edit.

## Changed: `BeneficiarioResponse` (used by `GET /api/beneficiarios`, `GET
/api/beneficiarios/{id}`, `POST /api/beneficiarios`, `PUT /api/beneficiarios/{id}`)

**Before**:

```json
{
  "id": "…",
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "pessoaId": "…",
  "pessoaNome": "…",
  "matricula": "…",
  "tipo": "TITULAR",
  "status": "ATIVO",
  "dataAdesao": "2026-01-01",
  "createdAt": "…",
  "updatedAt": "…"
}
```

**After**: identical, minus the `tenantId` field.

```json
{
  "id": "…",
  "pessoaId": "…",
  "pessoaNome": "…",
  "matricula": "…",
  "tipo": "TITULAR",
  "status": "ATIVO",
  "dataAdesao": "2026-01-01",
  "createdAt": "…",
  "updatedAt": "…"
}
```

(`BeneficiarioInput`, the request body for `POST`/`PUT`, already has no `tenantId` field — no
change there.)

## Unchanged: error responses

Tenant-isolation rejections continue to be RFC 7807 `ProblemDetail` bodies, produced by the same
`ApiExceptionHandler` paths as today:

- Missing/invalid `X-Tenant-Id` header → `400 Bad Request` (`TenantContextFilter`, unchanged).
- `X-Tenant-Id` not one of the caller's memberships (non-admin) → `403 Forbidden`
  (`TenantContextFilter`, unchanged).
- **New failure mode this feature introduces**: the database tenant context cannot be
  established for a transaction (FR-003) → `500 Internal Server Error`, via the existing
  catch-all `ApiExceptionHandler.handleUnexpected` — no new `ProblemDetail` shape, see
  research.md §4.

## Not a new endpoint: audit log (FR-013)

The `tenant_access_audit_log` table (data-model.md) has no corresponding REST endpoint in this
feature — nothing in the spec requires the audit trail to be readable through the API, only that
it be recorded.
