--liquibase formatted sql

--changeset tbm:1
CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    nome TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_tenant_membership (
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, tenant_id)
);

CREATE TABLE pessoa (
    id UUID PRIMARY KEY,
    nome TEXT NOT NULL,
    cpf VARCHAR(11) NOT NULL UNIQUE,
    data_nascimento DATE,
    email TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE beneficiario (
    id UUID PRIMARY KEY,
    pessoa_id UUID NOT NULL REFERENCES pessoa (id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL REFERENCES tenant (id) ON DELETE RESTRICT,
    matricula TEXT NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('TITULAR', 'DEPENDENTE')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO', 'INATIVO')),
    data_adesao DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_beneficiario_tenant_matricula UNIQUE (tenant_id, matricula)
);

CREATE INDEX idx_beneficiario_tenant_pessoa ON beneficiario (tenant_id, pessoa_id);
