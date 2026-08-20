package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.tbm.security.TenantSessionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Convergence finding T029: FR-003's fail-closed contract ("if establishing the tenant context
 * fails, abort the transaction and return an error") was previously verified only at the unit
 * level (TenantSessionContextTest, against a mocked EntityManager). This exercises the same
 * contract through a real HTTP request, matching the "new failure mode" documented in
 * contracts/beneficiario-api.md — a 500 RFC 7807 ProblemDetail via the existing catch-all
 * ApiExceptionHandler, not a raw stack trace.
 */
class TenantSessionContextFailureTest extends AbstractIntegrationTest {

    @MockBean private TenantSessionContext tenantSessionContext;

    @Test
    void aFailureToEstablishTheTenantContextAbortsWithA500ProblemDetail() {
        doThrow(new RuntimeException("simulated database failure"))
                .when(tenantSessionContext)
                .apply(any());

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("status")).isEqualTo(500);
        assertThat(response.getBody().get("title")).isEqualTo("Erro interno");
    }
}
