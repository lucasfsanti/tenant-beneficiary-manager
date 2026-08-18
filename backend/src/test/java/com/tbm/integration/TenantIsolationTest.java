package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantIsolationTest extends AbstractIntegrationTest {

    /** Seeded Beneficiario (V2__seed_demo_data.sql) that belongs to Tenant Beta, not Tenant Alfa. */
    private static final String TENANT_BETA_BENEFICIARIO_ID = "66666666-6666-6666-6666-666666666663";

    @Test
    void returns404ForRecordBelongingToAnotherTenant() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios/" + TENANT_BETA_BENEFICIARIO_ID,
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenEditingARecordBelongingToAnotherTenant() {
        Map<String, Object> updateInput =
                Map.of(
                        "pessoaId", "55555555-5555-5555-5555-555555555553",
                        "matricula", "MAT-B-001",
                        "tipo", "TITULAR",
                        "status", "INATIVO");
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios/" + TENANT_BETA_BENEFICIARIO_ID,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenDeletingARecordBelongingToAnotherTenant() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios/" + TENANT_BETA_BENEFICIARIO_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
