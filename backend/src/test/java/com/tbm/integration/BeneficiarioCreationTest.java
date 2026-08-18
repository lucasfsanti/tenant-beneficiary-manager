package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BeneficiarioCreationTest extends AbstractIntegrationTest {

    /** Seeded Pessoa (V2__seed_demo_data.sql) with no pre-existing Beneficiario in Tenant Alfa. */
    private static final String EXISTING_PESSOA_ID = "55555555-5555-5555-5555-555555555554";

    @Test
    void createsSuccessfullyWhenPessoaExists() {
        BeneficiarioInput input =
                new BeneficiarioInput(
                        UUID.fromString(EXISTING_PESSOA_ID),
                        "MAT-NEW-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().dataAdesao()).isEqualTo(LocalDate.now());
        assertThat(response.getBody().tenantId()).isEqualTo(UUID.fromString(TENANT_ALFA_ID));
    }

    @Test
    void honorsAnExplicitlySuppliedDataAdesao() {
        LocalDate explicitDate = LocalDate.of(2025, 6, 1);
        BeneficiarioInput input =
                new BeneficiarioInput(
                        UUID.fromString(EXISTING_PESSOA_ID),
                        "MAT-NEW-003",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        explicitDate);
        ResponseEntity<BeneficiarioResponse> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().dataAdesao()).isEqualTo(explicitDate);
    }

    @Test
    void rejectsCreationWhenPessoaDoesNotExist() {
        BeneficiarioInput input =
                new BeneficiarioInput(
                        UUID.randomUUID(),
                        "MAT-NEW-002",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
