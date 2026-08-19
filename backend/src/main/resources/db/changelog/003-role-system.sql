--liquibase formatted sql

-- Schema-only: adds the two role-standing columns. Demo-data seeding for this role system
-- (the System Admin user, bruno's Tenant Admin flag) lives separately in
-- 004-role-system-seed-data.sql, tagged context:demo — structural changes and demo-data
-- insertion are never mixed in the same changeset (spec 005-seed-data-relabel FR-013).
--changeset tbm:3
ALTER TABLE app_user ADD COLUMN is_system_admin BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE user_tenant_membership ADD COLUMN is_tenant_admin BOOLEAN NOT NULL DEFAULT false;
