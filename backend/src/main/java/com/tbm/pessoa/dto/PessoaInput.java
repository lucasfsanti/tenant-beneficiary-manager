package com.tbm.pessoa.dto;

import com.tbm.common.validation.Cpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record PessoaInput(
        @NotBlank(message = "nome é obrigatório") String nome,
        @Cpf(message = "CPF inválido") String cpf,
        LocalDate dataNascimento,
        @Email(message = "email inválido") String email) {
}
