package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantDeletionRestrictionTest extends AbstractIntegrationTest {

    @Test
    void deletionIsBlockedWhileBeneficiariosOrMembershipsReferenceTheTenant() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail")).isNotNull();
    }
}
