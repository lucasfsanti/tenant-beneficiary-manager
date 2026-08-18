package com.tbm.beneficiario.dto;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record BeneficiarioInput(
        @NotNull(message = "pessoaId é obrigatório") UUID pessoaId,
        @NotBlank(message = "matricula é obrigatória") String matricula,
        @NotNull(message = "tipo é obrigatório") BeneficiarioTipo tipo,
        @NotNull(message = "status é obrigatório") BeneficiarioStatus status,
        LocalDate dataAdesao) {
}
