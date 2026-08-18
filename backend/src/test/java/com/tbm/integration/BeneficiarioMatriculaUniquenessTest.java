package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BeneficiarioMatriculaUniquenessTest extends AbstractIntegrationTest {

    private static final String PESSOA_ID = "55555555-5555-5555-5555-555555555554";

    @Test
    void rejectsDuplicateMatriculaWithinSameTenant() {
        BeneficiarioInput input =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-DUP-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        restTemplate.exchange(
                "/api/beneficiarios",
                HttpMethod.POST,
                entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                BeneficiarioResponse.class);

        ResponseEntity<Map> duplicateResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void allowsSameMatriculaAcrossDifferentTenants() {
        BeneficiarioInput input =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-DUP-002",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> firstResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<BeneficiarioResponse> secondResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_BETA_ID)),
                        BeneficiarioResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
