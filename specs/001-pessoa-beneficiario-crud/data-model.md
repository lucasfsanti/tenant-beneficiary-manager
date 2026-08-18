# Phase 1 Data Model: Pessoa & Beneficiário Multitenant Registry

Single PostgreSQL database/schema (per research.md §1). Global tables carry no tenant column;
`beneficiario` carries the `tenant_id` discriminator that is the sole enforcement point for
isolation.

**Naming convention** (per spec.md Clarifications, 2026-08-15, corrected): entity and field names
are in Brazilian Portuguese where established (Pessoa, Beneficiário, nome, cpf, matrícula, tipo,
status, dataAdesao, etc.), and all UI-facing text is in Portuguese. "Tenant" is kept as-is and is
NOT renamed — an earlier draft of this document used "Cliente" instead; that rename has been
retracted per the corrected clarification.

## Entity: Pessoa (global — não pertence a nenhum tenant)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID, PK | generated |
| `nome` | text, not null | required (FR-001, FR-019) |
| `cpf` | text(11), not null, **unique** | required; validated format (check-digit valid CPF, FR-002); unique globally (FR-003) |
| `data_nascimento` | date | optional — not marked required in the field list; no format/range rule beyond being a valid date |
| `email` | text | optional |
| `created_at` / `updated_at` | timestamptz | audit columns, set automatically |

Relationships: one Pessoa → many Beneficiário (across different tenants; at most one active
Beneficiário per tenant is not required by spec, but matrícula-uniqueness is per-tenant, not
per-Pessoa — a Pessoa could in principle have more than one Beneficiário row in the same tenant
unless a business rule says otherwise; spec does not require preventing that, so it is allowed).

Deletion rule (FR-005): a Pessoa cannot be deleted while any `beneficiario.pessoa_id` references
it, in any tenant. Enforced by a foreign key with `ON DELETE RESTRICT` plus a pre-check in the
service layer to produce a clear RFC 7807 error (rather than surfacing a raw DB constraint
violation). Per the 2026-08-15 clarification, the error's `detail` message MUST be generic (e.g.,
"still linked to one or more Beneficiário records") and MUST NOT name or otherwise identify which
tenant(s) hold the reference — the pre-check only needs to know a reference exists (`EXISTS`
query), never which tenant it belongs to, to avoid leaking cross-tenant enrollment information.

## Entity: Tenant (global)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID, PK | generated |
| `nome` | text, not null, unique | display name shown by the tenant switcher and "active tenant" indicator (FR-016) |
| `created_at` | timestamptz | audit |

Seed data (research.md §6): ≥2 rows, deterministic ids/names for the quickstart guide.

## Entity: User (global) — platform account

| Field | Type | Rules |
|---|---|---|
| `id` | UUID, PK | generated |
| `username` | text, not null, unique | login identifier |
| `password_hash` | text, not null | BCrypt; simplified auth per research.md §2 |
| `created_at` | timestamptz | audit |

## Entity: UserTenantMembership (global, join table)

| Field | Type | Rules |
|---|---|---|
| `user_id` | UUID, FK → user.id | part of composite PK |
| `tenant_id` | UUID, FK → tenant.id | part of composite PK |

Determines exactly which tenants appear in a given user's tenant switcher and which `X-Tenant-Id`
values that user's requests may activate (FR-021). Seed data includes at least one user with
memberships in 2+ tenants, and at least one user restricted to exactly one tenant (FR-017) — the
latter makes the cross-tenant denial behavior (FR-021/SC-009) demonstrable from seed data alone.

## Entity: Beneficiário (escopo por tenant)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID, PK | generated |
| `pessoa_id` | UUID, FK → pessoa.id, not null | vincula a uma Pessoa global existente; must reference an existing Pessoa (FR-006/FR-007); `ON DELETE RESTRICT` |
| `tenant_id` | UUID, FK → tenant.id, not null | **isolation discriminator**, implícito pelo contexto da requisição — resolved server-side from the authenticated principal's active tenant (validated `X-Tenant-Id` against membership, research.md §1/§2); never accepted as a free client-supplied field on the record itself |
| `matricula` | text, not null | required; **unique dentro do tenant** — `UNIQUE (tenant_id, matricula)` (FR-009) |
| `tipo` | enum: `TITULAR`, `DEPENDENTE`, not null | required — identifies whether the Beneficiário is the primary holder or a dependent |
| `status` | enum: `ATIVO`, `INATIVO`, not null | exactly two values (supersedes the `ACTIVE`/`INACTIVE` naming from the 2026-08-15 clarification session — same two-value semantics, Portuguese labels) |
| `data_adesao` | date | enrollment/adhesion date within the tenant |
| `created_at` / `updated_at` | timestamptz | audit |

> Note: this supersedes the earlier `plan` (plano) field from the initial data model draft — the
> authoritative Beneficiário field set is exactly the one above (`pessoaId`, `tenantId`,
> `matricula`, `tipo`, `status`, `dataAdesao`), now consistently reflected across spec.md
> (FR-008/FR-019/FR-023/FR-024) and `contracts/openapi.yaml`.

Indexes: `(tenant_id, pessoa_id)` for lookups; `(tenant_id, matricula)` unique constraint doubles
as the natural filter/search index; a supporting index on a lower-cased Pessoa name (via join) is
delegated to the query plan — no denormalized name copy is introduced, since Pessoa is
authoritative and joins are cheap at this scale.

## State transitions

`status` (Beneficiário) is a simple two-value toggle (`ATIVO`/`INATIVO`), editable directly by an
authorized user (User Story 2, Acceptance Scenario 5) — no workflow/approval steps are defined by
the spec, so no transition table beyond "either value may be set to the other at any time" is
needed. `tipo` (`TITULAR`/`DEPENDENTE`) is likewise a directly editable classification with no
transition rules of its own.

## Validation summary (maps to Functional Requirements)

| Rule | Enforced at | FR |
|---|---|---|
| Nome required | Bean Validation on Pessoa DTO | FR-001, FR-019 |
| CPF required, valid format (check-digit valid) | Bean Validation (`@Cpf`) on Pessoa DTO | FR-002, FR-019 |
| CPF globally unique | DB unique constraint + service pre-check → 409 Problem Detail | FR-003 |
| Pessoa referenced by Beneficiário must exist | Service-layer lookup before insert → 400 Problem Detail | FR-007 |
| Matrícula unique per tenant | DB composite unique constraint + service pre-check → 409 Problem Detail | FR-009 |
| Required Beneficiário fields present (pessoaId, matrícula, tipo, status) | Bean Validation on Beneficiário DTO | FR-008, FR-019 |
| `tenantId` never trusted from free client input | Resolved server-side from `X-Tenant-Id` + membership check, not from the record payload | FR-021 |
| Beneficiário visible/mutable only in its own tenant | Tenant-scoped repository (research.md §1); cross-tenant id → 404 | FR-011 |
| Active tenant restricted to user's memberships | Security filter validates `X-Tenant-Id` against membership → 403 before reaching repository | FR-021 |
| Pessoa deletion blocked while referenced | FK `ON DELETE RESTRICT` + service pre-check → 409 Problem Detail | FR-005 |
