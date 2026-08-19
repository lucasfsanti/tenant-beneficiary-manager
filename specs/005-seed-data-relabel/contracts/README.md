# Contracts: Clearer, Expanded Demo Seed Data

No contract changes. This feature only touches migration/config files and demo data:

- No endpoint is added, removed, renamed, or moved.
- No request or response schema changes.
- No status code changes.
- The values returned by existing endpoints for renamed/new demo rows change (e.g.
  `GET /api/tenants` now returns `{"name": "Tenant 1"}` instead of `{"name": "Tenant Alfa"}` for
  that same tenant id) — but the **shape** of the response is identical; this is a data change,
  not a contract change.

The authoritative OpenAPI contract (`specs/003-rbac-user-roles/contracts/openapi.yaml`) remains
accurate as-is.
