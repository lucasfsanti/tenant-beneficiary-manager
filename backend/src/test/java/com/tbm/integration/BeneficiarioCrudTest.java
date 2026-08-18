package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import com.tbm.pessoa.dto.PessoaResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BeneficiarioCrudTest extends AbstractIntegrationTest {

    private static final String PESSOA_ID = "55555555-5555-5555-5555-555555555554";

    @Test
    void updatesAndDeletesWithoutAffectingLinkedPessoa() {
        BeneficiarioInput createInput =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-CRUD-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> createResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(createInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = createResponse.getBody().id();

        BeneficiarioInput updateInput =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-CRUD-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.INATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> updateResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().status()).isEqualTo(BeneficiarioStatus.INATIVO);

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<PessoaResponse> pessoaResponse =
                restTemplate.exchange(
                        "/api/pessoas/" + PESSOA_ID,
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        PessoaResponse.class);
        assertThat(pessoaResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsUpdateWhenPessoaDoesNotExist() {
        BeneficiarioInput createInput =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-CRUD-002",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> createResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(createInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        UUID id = createResponse.getBody().id();

        BeneficiarioInput updateInput =
                new BeneficiarioInput(
                        UUID.randomUUID(),
                        "MAT-CRUD-002",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<Map> updateResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        restTemplate.exchange(
                "/api/beneficiarios/" + id,
                HttpMethod.DELETE,
                entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                Void.class);
    }
}
