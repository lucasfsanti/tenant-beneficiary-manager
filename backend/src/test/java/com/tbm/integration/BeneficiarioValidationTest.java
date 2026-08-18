package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BeneficiarioValidationTest extends AbstractIntegrationTest {

    private static final String PESSOA_ID = "55555555-5555-5555-5555-555555555554";

    @Test
    void rejectsMissingPessoaId() {
        Map<String, Object> body = Map.of("matricula", "MAT-VAL-000", "tipo", "TITULAR", "status", "ATIVO");
        assertBadRequest(body);
    }

    @Test
    void rejectsMissingMatricula() {
        Map<String, Object> body = Map.of("pessoaId", PESSOA_ID, "tipo", "TITULAR", "status", "ATIVO");
        assertBadRequest(body);
    }

    @Test
    void rejectsMissingTipo() {
        Map<String, Object> body =
                Map.of("pessoaId", PESSOA_ID, "matricula", "MAT-VAL-001", "status", "ATIVO");
        assertBadRequest(body);
    }

    @Test
    void rejectsMissingStatus() {
        Map<String, Object> body =
                Map.of("pessoaId", PESSOA_ID, "matricula", "MAT-VAL-002", "tipo", "TITULAR");
        assertBadRequest(body);
    }

    @Test
    void rejectsInvalidTipoEnumValue() {
        Map<String, Object> body =
                Map.of(
                        "pessoaId",
                        PESSOA_ID,
                        "matricula",
                        "MAT-VAL-003",
                        "tipo",
                        "INVALIDO",
                        "status",
                        "ATIVO");
        assertBadRequest(body);
    }

    @Test
    void rejectsInvalidStatusEnumValue() {
        Map<String, Object> body =
                Map.of(
                        "pessoaId",
                        PESSOA_ID,
                        "matricula",
                        "MAT-VAL-004",
                        "tipo",
                        "TITULAR",
                        "status",
                        "INVALIDO");
        assertBadRequest(body);
    }

    private void assertBadRequest(Map<String, Object> body) {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(body, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
