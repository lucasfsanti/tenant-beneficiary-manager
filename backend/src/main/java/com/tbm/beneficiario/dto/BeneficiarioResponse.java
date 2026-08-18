package com.tbm.beneficiario.dto;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BeneficiarioResponse(
        UUID id,
        UUID tenantId,
        UUID pessoaId,
        String pessoaNome,
        String matricula,
        BeneficiarioTipo tipo,
        BeneficiarioStatus status,
        LocalDate dataAdesao,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
