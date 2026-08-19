# Contracts: User Self-Registration (Bootstrap Entrypoint)

One new endpoint. Per Constitution Principle III, the authoritative contract is the
springdoc-generated OpenAPI/Swagger UI (`/v3/api-docs`, `/swagger-ui.html`), produced from the
actual `AuthController` annotations once implemented — this file is a design-time description,
not a hand-maintained duplicate.

## `POST /api/auth/register`

**Auth**: None — this is the one intentionally public, unauthenticated write endpoint in the
system (spec FR-001).

**Request body**:

```json
{
  "username": "string, required, non-blank",
  "password": "string, required, non-blank"
}
```

No role field exists in the request — the granted role is never client-influenced (spec FR-011,
research.md §4).

**Responses**:

| Status | When | Body |
| --- | --- | --- |
| `204 No Content` | Account created successfully. | (none) |
| `400 Bad Request` | `username` or `password` missing/blank. | RFC 7807 `ProblemDetail` (existing `@Valid`/`MethodArgumentNotValidException` handling, unchanged shape). |
| `409 Conflict` | `username` already registered to an existing account. | RFC 7807 `ProblemDetail` (existing `ConflictException` handling, unchanged shape). |

**Side effects**: Creates exactly one new `app_user` row with zero `user_tenant_membership` rows.
`is_system_admin` is `true` only if this is the very first account ever created on the platform
(spec FR-003); `false` otherwise (spec FR-005). No session/token is issued — the client is
expected to proceed to `POST /api/auth/login` afterward (spec FR-009, Assumptions).

## Unchanged endpoints

`POST /api/auth/login`, `GET /api/me`, and every endpoint added by prior features are unaffected
— no request/response shape, status code, or security requirement changes for any existing
endpoint.
