package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import com.tbm.beneficiario.dto.BeneficiarioInput;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Deserializes into {@code Map} rather than the typed {@code BeneficiarioResponse} record, so
 * the "no tenantId key" assertion actually exercises the raw JSON body instead of trivially
 * passing because the Java DTO no longer has the field (User Story 2, FR-002, SC-001).
 */
class BeneficiarioResponseContractTest extends AbstractIntegrationTest {

    private static final String EXISTING_PESSOA_ID = "55555555-5555-5555-5555-555555555554";

    @Test
    void noBeneficiarioResponseContainsATenantIdField() {
        BeneficiarioInput createInput =
                new BeneficiarioInput(
                        UUID.fromString(EXISTING_PESSOA_ID),
                        "MAT-CONTRACT-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<Map> createResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(createInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).doesNotContainKey("tenantId");
        String id = (String) createResponse.getBody().get("id");

        ResponseEntity<Map> getResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).doesNotContainKey("tenantId");

        BeneficiarioInput updateInput =
                new BeneficiarioInput(
                        UUID.fromString(EXISTING_PESSOA_ID),
                        "MAT-CONTRACT-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.INATIVO,
                        null);
        ResponseEntity<Map> updateResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).doesNotContainKey("tenantId");

        ResponseEntity<Map> listResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var content = (java.util.List<Map<String, Object>>) listResponse.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(item -> assertThat(item).doesNotContainKey("tenantId"));
    }
}
