# Contracts: Searchable Select Filters

No new endpoint. One existing endpoint's matching/result-shaping behavior changes. Per
Constitution Principle III, the authoritative contract is the springdoc-generated OpenAPI/Swagger
UI (`/v3/api-docs`, `/swagger-ui.html`), produced from the actual `UserController` annotations
once implemented — this file is a design-time description, not a hand-maintained duplicate.

## `GET /api/users` (changed)

**Auth**: Unchanged — any authenticated user (`@SecurityRequirement(name = "bearerAuth")`).

**Request**: Unchanged shape — `?username=<text>` query parameter.

| | Before | After |
|---|---|---|
| Matching | Exact match only | Case-insensitive substring match |
| Minimum `username` length | None | 2 characters (after trimming); shorter input returns `[]` without querying |
| Maximum results | 1 (exact match can only ever find one row) | 20 |
| Ordering | N/A (0 or 1 result) | Ascending by `username` |

**Responses**: Unchanged shape — `200 OK` with a JSON array of `UserSummary` (`{ id, username }`),
possibly empty. No new status code, no new error shape; a below-minimum-length query is treated as
"nothing to show yet," not an error (spec Edge Cases, research.md §3).

**Swagger annotation**: The controller's `@Tag` description ("busca de usuários... por username
exato") MUST be updated in the same change to describe substring matching and the
minimum-length/cap behavior (Constitution Principle III).

## Unchanged endpoints

Every other endpoint — including `GET /api/pessoas` and `GET /api/beneficiarios`, whose substring
matching behavior predates this feature and is not modified by it — is unaffected: no
request/response shape, status code, or security requirement changes.
