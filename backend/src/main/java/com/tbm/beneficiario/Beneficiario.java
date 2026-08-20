package com.tbm.beneficiario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vw_beneficiario")
public class Beneficiario {

    @Id
    private UUID id;

    @Column(name = "pessoa_id", nullable = false)
    private UUID pessoaId;

    @Column(nullable = false)
    private String matricula;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BeneficiarioTipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BeneficiarioStatus status;

    @Column(name = "data_adesao", nullable = false)
    private LocalDate dataAdesao;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPessoaId() {
        return pessoaId;
    }

    public void setPessoaId(UUID pessoaId) {
        this.pessoaId = pessoaId;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public BeneficiarioTipo getTipo() {
        return tipo;
    }

    public void setTipo(BeneficiarioTipo tipo) {
        this.tipo = tipo;
    }

    public BeneficiarioStatus getStatus() {
        return status;
    }

    public void setStatus(BeneficiarioStatus status) {
        this.status = status;
    }

    public LocalDate getDataAdesao() {
        return dataAdesao;
    }

    public void setDataAdesao(LocalDate dataAdesao) {
        this.dataAdesao = dataAdesao;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
