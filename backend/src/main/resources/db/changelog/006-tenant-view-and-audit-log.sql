--liquibase formatted sql

-- Moves Beneficiário tenant filtering from application-code WHERE clauses to the database
-- itself: reads/writes now go through vw_beneficiario, a view that filters by the
-- transaction-scoped session variable app.tenant_id (set via set_config(..., true) before every
-- query, see TenantSessionContext). The base beneficiario table keeps its existing name and
-- columns unchanged except for tenant_id's new DEFAULT, which lets INSERTs issued through the
-- view (which never mention tenant_id, since the JPA entity no longer has that field) get it
-- stamped automatically from the same session variable (spec 007-tenant-transparent-views
-- research.md §1, §2, §5).
--
-- NULLIF(current_setting('app.tenant_id', true), '') is the standard Postgres idiom for a
-- session variable that may be in either of two "unset" states: truly never referenced on this
-- connection (current_setting(..., true) alone already returns NULL for that case), or
-- previously set via set_config at least once and since reset (PostgreSQL then registers it as a
-- session placeholder, and current_setting on it returns '' — an empty string, not NULL — for
-- any transaction that doesn't currently have it locally set; missing_ok=true has no effect once
-- the placeholder exists, since the parameter is no longer "missing"). Casting '' to ::uuid
-- raises a raw PSQLException instead of degrading to "no match", which would violate User Story
-- 1's Acceptance Scenario 2 (a transaction with no tenant context returns no rows, not an
-- error). NULLIF folds both unset states down to NULL uniformly, so the WHERE comparison and the
-- INSERT default both fail closed the same way regardless of the connection's history: no rows
-- for reads, a NOT NULL violation for writes.
--changeset tbm:6
ALTER TABLE beneficiario ALTER COLUMN tenant_id
    SET DEFAULT NULLIF(current_setting('app.tenant_id', true), '')::uuid;

CREATE VIEW vw_beneficiario AS
SELECT id, pessoa_id, matricula, tipo, status, data_adesao, created_at, updated_at
FROM beneficiario
WHERE tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid;

-- Records every System Admin access to a tenant they are not a member of (FR-013), written by
-- TenantContextFilter at the moment it grants the cross-tenant bypass (research.md §7).
CREATE TABLE tenant_access_audit_log (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL REFERENCES app_user (id),
    target_tenant_id UUID NOT NULL REFERENCES tenant (id),
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
