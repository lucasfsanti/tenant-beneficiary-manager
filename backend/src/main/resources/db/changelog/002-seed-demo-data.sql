--liquibase formatted sql

--changeset tbm:2 context:demo
-- Demo tenants
INSERT INTO tenant (id, nome, created_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Tenant Alfa', now()),
    ('22222222-2222-2222-2222-222222222222', 'Tenant Beta', now());

-- Demo users. Password for both is "demo123" (BCrypt hash, strength 10).
INSERT INTO app_user (id, username, password_hash, created_at) VALUES
    ('33333333-3333-3333-3333-333333333333', 'ana', '$2b$10$K1faUDGXmVIgKrNryKGFaOEK4hECPRiNkz6qokAmAPbfklbmaTDo6', now()),
    ('44444444-4444-4444-4444-444444444444', 'bruno', '$2b$10$K1faUDGXmVIgKrNryKGFaOEK4hECPRiNkz6qokAmAPbfklbmaTDo6', now());

-- ana is a member of both tenants (FR-017); bruno is a member of Tenant Alfa only.
INSERT INTO user_tenant_membership (user_id, tenant_id) VALUES
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111'),
    ('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222'),
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111');

-- Demo Pessoas (global, not tenant-scoped)
INSERT INTO pessoa (id, nome, cpf, data_nascimento, email, created_at, updated_at) VALUES
    ('55555555-5555-5555-5555-555555555551', 'Maria Silva', '92239444657', '1985-04-12', 'maria.silva@example.com', now(), now()),
    ('55555555-5555-5555-5555-555555555552', 'João Souza', '75678428403', '1990-09-23', 'joao.souza@example.com', now(), now()),
    ('55555555-5555-5555-5555-555555555553', 'Carla Pereira', '06433433189', '1978-01-05', NULL, now(), now()),
    ('55555555-5555-5555-5555-555555555554', 'Pedro Santos', '81018804293', NULL, 'pedro.santos@example.com', now(), now());

-- Demo Beneficiarios, split across tenants (matricula unique per tenant only)
INSERT INTO beneficiario (id, pessoa_id, tenant_id, matricula, tipo, status, data_adesao, created_at, updated_at) VALUES
    ('66666666-6666-6666-6666-666666666661', '55555555-5555-5555-5555-555555555551', '11111111-1111-1111-1111-111111111111', 'MAT-A-001', 'TITULAR', 'ATIVO', '2026-01-15', now(), now()),
    ('66666666-6666-6666-6666-666666666662', '55555555-5555-5555-5555-555555555552', '11111111-1111-1111-1111-111111111111', 'MAT-A-002', 'DEPENDENTE', 'ATIVO', '2026-02-01', now(), now()),
    ('66666666-6666-6666-6666-666666666663', '55555555-5555-5555-5555-555555555553', '22222222-2222-2222-2222-222222222222', 'MAT-B-001', 'TITULAR', 'ATIVO', '2026-01-20', now(), now()),
    ('66666666-6666-6666-6666-666666666664', '55555555-5555-5555-5555-555555555551', '22222222-2222-2222-2222-222222222222', 'MAT-B-002', 'DEPENDENTE', 'INATIVO', '2025-11-10', now(), now());
