--liquibase formatted sql

-- Relabels the existing demo seed data with a systematic, self-describing naming convention
-- ("Tenant N", "Pessoa N", "User N - ROLE", "Beneficiário N - Tenant M") and expands the demo
-- roster (spec 005-seed-data-relabel). Every UPDATE below matches by the row's existing,
-- unchanged id — no relationship (tenant membership, System Admin/Tenant Admin standing) is
-- altered, only display fields (spec FR-005). Gated by context:demo, same as 002/004.
--changeset tbm:5 context:demo

-- Rename existing tenants (ids unchanged).
UPDATE tenant SET nome = 'Tenant 1' WHERE id = '11111111-1111-1111-1111-111111111111';
UPDATE tenant SET nome = 'Tenant 2' WHERE id = '22222222-2222-2222-2222-222222222222';

-- Rename existing users (ids, memberships, and admin standing unchanged).
UPDATE app_user SET username = 'User 1 - NORMAL' WHERE id = '33333333-3333-3333-3333-333333333333';
UPDATE app_user SET username = 'User 2 - TENANT ADMIN' WHERE id = '44444444-4444-4444-4444-444444444444';
UPDATE app_user SET username = 'User 3 - ADMIN' WHERE id = '77777777-7777-7777-7777-777777777777';

-- Rename existing pessoas (ids unchanged).
UPDATE pessoa SET nome = 'Pessoa 1' WHERE id = '55555555-5555-5555-5555-555555555551';
UPDATE pessoa SET nome = 'Pessoa 2' WHERE id = '55555555-5555-5555-5555-555555555552';
UPDATE pessoa SET nome = 'Pessoa 3' WHERE id = '55555555-5555-5555-5555-555555555553';
UPDATE pessoa SET nome = 'Pessoa 4' WHERE id = '55555555-5555-5555-5555-555555555554';

-- Rename existing beneficiarios (ids unchanged).
UPDATE beneficiario SET matricula = 'Beneficiário 1 - Tenant 1' WHERE id = '66666666-6666-6666-6666-666666666661';
UPDATE beneficiario SET matricula = 'Beneficiário 2 - Tenant 1' WHERE id = '66666666-6666-6666-6666-666666666662';
UPDATE beneficiario SET matricula = 'Beneficiário 1 - Tenant 2' WHERE id = '66666666-6666-6666-6666-666666666663';
UPDATE beneficiario SET matricula = 'Beneficiário 2 - Tenant 2' WHERE id = '66666666-6666-6666-6666-666666666664';

-- Expand the demo roster (spec FR-007). New Tenants (ids use a distinct hex-letter block so
-- they read as clearly "new" alongside the renamed numeric-block ids above).
INSERT INTO tenant (id, nome, created_at) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Tenant 3', now()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Tenant 4', now());

-- New Pessoas. CPFs are freshly computed, check-digit-valid values (same algorithm as
-- CpfValidator.java), verified not to collide with any CPF already seeded or hardcoded in the
-- test suite. Pessoa 8 is deliberately left unlinked to any Beneficiario (free for manual testing).
INSERT INTO pessoa (id, nome, cpf, data_nascimento, email, created_at, updated_at) VALUES
    ('cccccccc-cccc-cccc-cccc-ccccccccccc1', 'Pessoa 5', '91283746573', '1988-03-10', 'pessoa5@example.com', now(), now()),
    ('cccccccc-cccc-cccc-cccc-ccccccccccc2', 'Pessoa 6', '23456789173', '1992-07-22', NULL, now(), now()),
    ('cccccccc-cccc-cccc-cccc-ccccccccccc3', 'Pessoa 7', '74192638509', NULL, 'pessoa7@example.com', now(), now()),
    ('cccccccc-cccc-cccc-cccc-ccccccccccc4', 'Pessoa 8', '47296183004', '1995-12-01', 'pessoa8@example.com', now(), now());

-- New Users. Same shared password ("demo123", same BCrypt hash) as every other seeded user
-- (spec FR-006). User 4 is a second, independent Tenant Admin (Tenant 3 only — no overlap with
-- User 2's Tenant 1 standing, spec FR-008). User 6 is a Normal member of exactly one tenant
-- (spec FR-009); User 5 mirrors User 1's multi-tenant Normal shape, but in the new tenants.
INSERT INTO app_user (id, username, password_hash, created_at) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'User 4 - TENANT ADMIN', '$2b$10$K1faUDGXmVIgKrNryKGFaOEK4hECPRiNkz6qokAmAPbfklbmaTDo6', now()),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'User 5 - NORMAL', '$2b$10$K1faUDGXmVIgKrNryKGFaOEK4hECPRiNkz6qokAmAPbfklbmaTDo6', now()),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 'User 6 - NORMAL', '$2b$10$K1faUDGXmVIgKrNryKGFaOEK4hECPRiNkz6qokAmAPbfklbmaTDo6', now());

INSERT INTO user_tenant_membership (user_id, tenant_id, is_tenant_admin) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', true),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', false),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', false),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', '22222222-2222-2222-2222-222222222222', false);

-- New Beneficiarios — gives every tenant at least one (spec FR-010): Tenant 3 gets two, Tenant 4
-- gets one. Pessoa 8 stays unlinked (free for manual testing).
INSERT INTO beneficiario (id, pessoa_id, tenant_id, matricula, tipo, status, data_adesao, created_at, updated_at) VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd1', 'cccccccc-cccc-cccc-cccc-ccccccccccc1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Beneficiário 1 - Tenant 3', 'TITULAR', 'ATIVO', '2026-03-01', now(), now()),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd2', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Beneficiário 2 - Tenant 3', 'DEPENDENTE', 'ATIVO', '2026-03-05', now(), now()),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd3', 'cccccccc-cccc-cccc-cccc-ccccccccccc3', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Beneficiário 1 - Tenant 4', 'TITULAR', 'ATIVO', '2026-03-10', now(), now());
