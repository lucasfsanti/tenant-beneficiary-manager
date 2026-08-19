package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantMembershipEnforcementTest extends AbstractIntegrationTest {

    private static final String BRUNO_USERNAME = "User 2 - TENANT ADMIN";
    private static final String BRUNO_PASSWORD = "demo123";

    /** bruno ("User 2 - TENANT ADMIN" as of 005-seed-data-relabel-and-expand.sql) is a member of
     * Tenant 1 only, not Tenant 2. */
    @Test
    void rejectsTenantHeaderNotInCallersMemberships() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, TENANT_BETA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsUnauthenticatedRequestToATenantScopedEndpoint() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, new HttpHeaders()),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsMissingTenantHeaderOnATenantScopedEndpoint() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsABlankTenantHeaderValue() {
        HttpHeaders headers = authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null);
        headers.set("X-Tenant-Id", "   ");
        ResponseEntity<Map> response =
                restTemplate.exchange("/api/beneficiarios", HttpMethod.GET, entity(null, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsAMalformedTenantHeaderValue() {
        HttpHeaders headers = authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null);
        headers.set("X-Tenant-Id", "not-a-valid-uuid");
        ResponseEntity<Map> response =
                restTemplate.exchange("/api/beneficiarios", HttpMethod.GET, entity(null, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
