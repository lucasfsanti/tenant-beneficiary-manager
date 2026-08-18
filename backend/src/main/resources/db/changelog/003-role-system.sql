--liquibase formatted sql

--changeset tbm:3
ALTER TABLE app_user ADD COLUMN is_system_admin BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE user_tenant_membership ADD COLUMN is_tenant_admin BOOLEAN NOT NULL DEFAULT false;

-- New demo System Admin. Password is "demo123" (same BCrypt hash as the other seed users).
-- No tenant memberships: System Admin standing is platform-wide (FR-008).
INSERT INTO app_user (id, username, password_hash, created_at, is_system_admin) VALUES
    ('77777777-7777-7777-7777-777777777777', 'admin', '$2b$10$K1faUDGXmVIgKrNryKGFaOEK4hECPRiNkz6qokAmAPbfklbmaTDo6', now(), true);

-- bruno becomes Tenant Admin of Tenant Alfa (his only membership), demonstrating that tier
-- from seed data without adding a new persona (research.md §7).
UPDATE user_tenant_membership SET is_tenant_admin = true
    WHERE user_id = '44444444-4444-4444-4444-444444444444'
      AND tenant_id = '11111111-1111-1111-1111-111111111111';
