package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantDeletionRestrictionTest extends AbstractIntegrationTest {

    private static final String ADMIN_ID = "77777777-7777-7777-7777-777777777777";

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

    @Test
    void deletionIsBlockedByAMembershipAloneWithNoBeneficiarioRecords() {
        // Distinct from the case above: this tenant has a membership but zero Beneficiario
        // records, so the block must be triggered by the membership check specifically.
        Map<String, String> createInput = Map.of("name", "Membership-Only Block Test " + UUID.randomUUID());
        String tenantId =
                (String)
                        restTemplate
                                .exchange(
                                        "/api/tenants",
                                        HttpMethod.POST,
                                        entity(createInput, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                                        Map.class)
                                .getBody()
                                .get("id");
        try {
            restTemplate.exchange(
                    "/api/tenants/" + tenantId + "/members",
                    HttpMethod.POST,
                    entity(Map.of("userId", ADMIN_ID), authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                    Map.class);

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            "/api/tenants/" + tenantId,
                            HttpMethod.DELETE,
                            entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                            Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            restTemplate.exchange(
                    "/api/tenants/" + tenantId + "/members/" + ADMIN_ID,
                    HttpMethod.DELETE,
                    entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                    Void.class);
            restTemplate.exchange(
                    "/api/tenants/" + tenantId,
                    HttpMethod.DELETE,
                    entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                    Void.class);
        }
    }
}
