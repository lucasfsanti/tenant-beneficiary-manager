package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MembershipManagementTest extends AbstractIntegrationTest {

    private static final String ADMIN_ID = "77777777-7777-7777-7777-777777777777";
    private static final String ANA_ID = "33333333-3333-3333-3333-333333333333";
    private static final String BRUNO_ID = "44444444-4444-4444-4444-444444444444";

    @Test
    void freshlyCreatedTenantIsImmediatelyAssignableToMemberships() {
        // SC-001: a System Admin (who has every Tenant Admin capability everywhere, FR-008) can
        // add a membership to a Tenant they just created.
        Map<String, String> createInput = Map.of("name", "Membership SC-001 Test " + UUID.randomUUID());
        ResponseEntity<Map> createResponse =
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.POST,
                        entity(createInput, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tenantId = (String) createResponse.getBody().get("id");

        ResponseEntity<Map> addResponse =
                restTemplate.exchange(
                        "/api/tenants/" + tenantId + "/members",
                        HttpMethod.POST,
                        entity(Map.of("userId", ADMIN_ID), authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Cleanup: remove membership then delete the now-empty tenant.
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

    @Test
    void tenantAdminAddsAndRemovesMembersOfTheirOwnTenant() {
        ResponseEntity<Map> addResponse =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID + "/members",
                        HttpMethod.POST,
                        entity(Map.of("userId", ADMIN_ID), authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List> listResponse =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID + "/members",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).anyMatch(m -> ADMIN_ID.equals(((Map<?, ?>) m).get("userId")));
        // Each member's tier within the tenant must be visible (US2/AC5): bruno is Tenant Admin
        // of Alfa, the freshly-added admin is not (their standing there is System-Admin-derived,
        // not membership-level).
        assertThat(listResponse.getBody())
                .anyMatch(
                        m ->
                                BRUNO_ID.equals(((Map<?, ?>) m).get("userId"))
                                        && Boolean.TRUE.equals(((Map<?, ?>) m).get("isTenantAdmin")));
        assertThat(listResponse.getBody())
                .anyMatch(
                        m ->
                                ADMIN_ID.equals(((Map<?, ?>) m).get("userId"))
                                        && Boolean.FALSE.equals(((Map<?, ?>) m).get("isTenantAdmin")));

        ResponseEntity<Void> removeResponse =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID + "/members/" + ADMIN_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Void.class);
        assertThat(removeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void removingAMembershipRevokesBeneficiarioAccessImmediately() {
        // ana (User 1 - NORMAL) is a genuine Normal-tier member of Tenant 1 (seed data) — removing her
        // membership must deny her the very next request, not just return 204 (US2/AC2).
        try {
            ResponseEntity<Void> removeResponse =
                    restTemplate.exchange(
                            "/api/tenants/" + TENANT_ALFA_ID + "/members/" + ANA_ID,
                            HttpMethod.DELETE,
                            entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                            Void.class);
            assertThat(removeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            ResponseEntity<Map> beneficiariosResponse =
                    restTemplate.exchange(
                            "/api/beneficiarios",
                            HttpMethod.GET,
                            entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                            Map.class);
            assertThat(beneficiariosResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            // Restore the seed baseline regardless of assertion outcome.
            restTemplate.exchange(
                    "/api/tenants/" + TENANT_ALFA_ID + "/members",
                    HttpMethod.POST,
                    entity(Map.of("userId", ANA_ID), authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                    Map.class);
        }
    }

    @Test
    void tenantAdminIsDeniedForATenantTheyDoNotAdminister() {
        ResponseEntity<Map> addResponse =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_BETA_ID + "/members",
                        HttpMethod.POST,
                        entity(Map.of("userId", ADMIN_ID), authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Void> removeResponse =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_BETA_ID + "/members/" + ADMIN_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Void.class);
        assertThat(removeResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void addingMembershipForNonexistentUserReturnsNotFound() {
        String bogusId = "99999999-9999-9999-9999-999999999999";
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID + "/members",
                        HttpMethod.POST,
                        entity(Map.of("userId", bogusId), authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void addingMembershipToANonexistentTenantReturnsNotFound() {
        String unknownTenantId = UUID.randomUUID().toString();
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/tenants/" + unknownTenantId + "/members",
                        HttpMethod.POST,
                        entity(Map.of("userId", ADMIN_ID), authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
