# Phase 1 Data Model: User Self-Registration (Bootstrap Entrypoint)

No new entity and no schema change (research.md §2). This feature only creates new rows in the
already-existing `User account` entity (`app_user`, first introduced in feature 001, extended
with `is_system_admin` in feature 003).

## User account (`app_user`) — existing entity, new creation path

| Field | Type | Notes for this feature |
| --- | --- | --- |
| `id` | UUID | Generated at creation time, same as every other account-creation path in this codebase. |
| `username` | text, unique | Supplied by the visitor; rejected with `409 Conflict` if already taken (spec FR-002). |
| `password_hash` | text | Derived from the visitor-supplied plaintext password via the platform's existing `PasswordEncoder` (spec FR-007) — the plaintext itself is never persisted. |
| `created_at` | timestamp | Set at creation, same as every other account. |
| `is_system_admin` | boolean | `true` only for the very first account ever created (`app_user` row count was 0 at the moment of creation, decided under the advisory lock from research.md §1); `false` for every account after that (spec FR-003, FR-005). Never influenced by anything the client submits (spec FR-011). |

## Role decision rule (not a stored field — computed once, at creation time)

```text
acquire pg_advisory_xact_lock(<constant>)
if app_user row count == 0:
    new account.is_system_admin = true
else:
    new account.is_system_admin = false
insert new account
(lock releases automatically on commit)
```

This rule is evaluated exactly once, at the moment a given account is inserted. It is never
re-evaluated or re-applied to an existing row — there is no path in this feature that changes an
existing account's `is_system_admin` value (that remains the exclusive job of the existing
grant/revoke capability from features 003/004, per spec FR-010).

## `user_tenant_membership` — existing entity, untouched by this feature

Every account created through this feature starts with **zero** rows in
`user_tenant_membership`, regardless of which role it receives (spec FR-003, FR-005). "System
Admin with no memberships" is already representable and already exists in seed data (the seeded
System Admin from feature 003/005 has zero memberships by design — System Admin standing is
platform-wide, not tenant-scoped). "Normal with no memberships" is a new-but-already-supported
shape: the schema has no constraint requiring a Normal account to belong to any tenant, so the
row simply has no membership rows until an existing Tenant Admin or System Admin adds one via the
platform's existing membership-management capability (feature 003) — no new relationship or
column is needed to express it.
