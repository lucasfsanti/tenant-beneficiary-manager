# Contracts: Migrate Admin Role Verification to Spring Security Authorization

No contract changes. This feature is an internal authorization-mechanism refactor (FR-007:
behavior parity is required), not a new or altered capability, so:

- No endpoint is added, removed, renamed, or moved.
- No request or response schema changes.
- No status code changes for any success path.
- **403 responses are unchanged in shape**: still an RFC 7807 `ProblemDetail` with
  `status: 403` and `title: "Acesso negado"`. The response is now produced by a new
  `AccessDeniedHandler` (research.md §4) instead of `ApiExceptionHandler`'s
  `ForbiddenException` mapping, but the JSON a client receives is the same.

The authoritative OpenAPI contract for every endpoint touched by this migration (`/api/tenants/**`,
`/api/tenants/{tenantId}/members/**`, `/api/users/{userId}/system-admin`) remains
[`specs/003-rbac-user-roles/contracts/openapi.yaml`](../../003-rbac-user-roles/contracts/openapi.yaml),
generated from/verified against the same controllers this feature touches. See
`data-model.md`'s Protected Operations table for the per-operation required-standing mapping this
migration must preserve.
