package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantMembershipEnforcementTest extends AbstractIntegrationTest {

    private static final String BRUNO_USERNAME = "bruno";
    private static final String BRUNO_PASSWORD = "demo123";

    /** bruno (seed data) is a member of Tenant Alfa only, not Tenant Beta. */
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
}
