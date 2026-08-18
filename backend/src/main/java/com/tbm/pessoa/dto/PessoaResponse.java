package com.tbm.pessoa.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PessoaResponse(
        UUID id,
        String nome,
        String cpf,
        LocalDate dataNascimento,
        String email,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
