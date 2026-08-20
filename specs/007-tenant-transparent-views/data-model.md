# Data Model: Transparent Tenant Scoping via Database Views

## Beneficiário (`vw_beneficiario` — a view over the `beneficiario` base table)

Represents the tenant-scoped business record (unchanged in business meaning from prior
features; only its storage/access shape changes here). The base table keeps its existing name,
`beneficiario`; the new filtering view follows the project's `vw_` naming convention
(clarification session, 2026-08-20) — this is an internal persistence-mapping detail, invisible
to the REST API.

| Field (entity) | Column (view + base table) | Type | Notes |
|---|---|---|---|
| `id` | `id` | UUID, PK | unchanged |
| `pessoaId` | `pessoa_id` | UUID, NOT NULL, FK → `pessoa.id` | unchanged |
| ~~`tenantId`~~ | `tenant_id` | UUID, NOT NULL, FK → `tenant.id` | **removed from the entity.** Still exists on the base table `beneficiario`; the view's `WHERE` clause filters by it but does not select it. Gains `DEFAULT current_setting('app.tenant_id')::uuid` so inserts through the view populate it automatically (research.md §2). |
| `matricula` | `matricula` | text, NOT NULL | unchanged; `UNIQUE (tenant_id, matricula)` constraint stays on the base table, now enforced implicitly per-tenant via the same mechanism |
| `tipo` | `tipo` | enum (`TITULAR`\|`DEPENDENTE`) | unchanged |
| `status` | `status` | enum (`ATIVO`\|`INATIVO`) | unchanged |
| `dataAdesao` | `data_adesao` | date, NOT NULL | unchanged |
| `createdAt` | `created_at` | timestamptz, NOT NULL | unchanged |
| `updatedAt` | `updated_at` | timestamptz, NOT NULL | unchanged |

**Validation rules** (unchanged, still enforced at the base-table/service level): `matricula`
unique per tenant; `pessoaId` must reference an existing Pessoa (service-level check,
`BeneficiarioService.create`/`update`).

**Access rule (new)**: every read or write goes through the `vw_beneficiario` view, which returns
only rows where `tenant_id = current_setting('app.tenant_id')::uuid` — the session variable set
per-transaction (see Active Tenant Context below). There is no code path that queries the base
`beneficiario` table directly except the one described under Tenant (System Admin deletion
guard).

## Active Tenant Context (new concept, not a persisted entity)

The per-request tenant, resolved exactly as today (client-sent `X-Tenant-Id` header validated
against the caller's JWT tenant memberships, or accepted unconditionally for a System Admin —
`TenantContextFilter`), held in the existing request-scoped `TenantContext` `ThreadLocal`, and
now **additionally** applied to the database session for the lifetime of each transaction via
`set_config('app.tenant_id', <value>, true)` (`TenantSessionContext`, research.md §1/§3).

- Lifetime: one PostgreSQL transaction (`is_local = true` → auto-reset at `COMMIT`/`ROLLBACK`,
  never leaks across pooled-connection reuse).
- Source of truth for *who* has access: unchanged (`TenantContextFilter` + `UserTenantMembership`).
- Source of truth for *what the database will return*: the session variable, read by the
  `vw_beneficiario` view's `WHERE` clause and by `beneficiario.tenant_id`'s `DEFAULT`
  expression.

## Tenant Access Audit Log (new entity/table — FR-013)

Records every System Admin use of the cross-tenant Beneficiário access path (User Story 3).

| Field (entity) | Column | Type | Notes |
|---|---|---|---|
| `id` | `id` | UUID, PK | generated at write time |
| `adminUserId` | `admin_user_id` | UUID, NOT NULL, FK → `app_user.id` | the acting System Admin |
| `targetTenantId` | `target_tenant_id` | UUID, NOT NULL, FK → `tenant.id` | the tenant being accessed, which the admin is not a member of |
| `accessedAt` | `accessed_at` | timestamptz, NOT NULL, `DEFAULT now()` | when the bypass was granted |

**Lifecycle**: insert-only, written once per bypassed request by `TenantContextFilter` at the
moment it grants the bypass (research.md §7); never updated or deleted by the application. No
API surface is added to read this table back — the spec requires the record to exist (FR-013,
SC-006), not that it be queryable through this feature.

## Unaffected entities (explicitly out of scope — FR-009 and spec Assumptions)

- **Pessoa** — global, never tenant-filtered; untouched by this feature.
- **Tenant** — organizational boundary; untouched as a concept. Its own management endpoints
  (`TenantController`/`TenantService`) keep taking an explicit `tenantId`, per spec Assumptions.
  One internal call site (`TenantService.delete`) changes *how* it checks for existing
  Beneficiário rows — see research.md §6 — but the `Tenant` entity/table itself is unchanged.
- **AppUser**, **UserTenantMembership** — unchanged; still the source of truth for who may
  select which tenant.
