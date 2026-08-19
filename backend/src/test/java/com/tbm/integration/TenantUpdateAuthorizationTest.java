package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantUpdateAuthorizationTest extends AbstractIntegrationTest {

    @Test
    void tenantAdminCanUpdateTheirOwnTenantsName() {
        String newName = "Tenant 1 Renomeado " + UUID.randomUUID();
        try {
            ResponseEntity<Map> response = update(TENANT_ALFA_ID, newName, BRUNO_USERNAME, BRUNO_PASSWORD);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("name")).isEqualTo(newName);
        } finally {
            update(TENANT_ALFA_ID, "Tenant 1", ADMIN_USERNAME, ADMIN_PASSWORD);
        }
    }

    @Test
    void systemAdminCanUpdateAnyTenantsName() {
        String newName = "Tenant 1 Renomeado " + UUID.randomUUID();
        try {
            ResponseEntity<Map> response = update(TENANT_ALFA_ID, newName, ADMIN_USERNAME, ADMIN_PASSWORD);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            update(TENANT_ALFA_ID, "Tenant 1", ADMIN_USERNAME, ADMIN_PASSWORD);
        }
    }

    @Test
    void normalTierMemberIsDenied() {
        ResponseEntity<Map> response = update(TENANT_ALFA_ID, "Should Not Apply", ANA_USERNAME, ANA_PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void callerWithNoStandingForThisTenantIsDenied() {
        // bruno has no standing at all for Tenant 2.
        ResponseEntity<Map> response = update(TENANT_BETA_ID, "Should Not Apply", BRUNO_USERNAME, BRUNO_PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Map> update(String tenantId, String name, String username, String password) {
        return restTemplate.exchange(
                "/api/tenants/" + tenantId,
                HttpMethod.PUT,
                entity(Map.of("name", name), authHeaders(username, password, null)),
                Map.class);
    }
}
