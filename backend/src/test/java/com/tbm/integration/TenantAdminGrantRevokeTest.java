package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantAdminGrantRevokeTest extends AbstractIntegrationTest {

    private static final String ANA_ID = "33333333-3333-3333-3333-333333333333";
    private static final String BRUNO_ID = "44444444-4444-4444-4444-444444444444";
    private static final String ADMIN_ID = "77777777-7777-7777-7777-777777777777";

    @Test
    void grantAndRevokeTakeEffectImmediatelyAndAreIdempotent() {
        // Grant: bruno (Tenant Admin of Alfa) promotes ana within Alfa; visible on her very next
        // /api/me call (FR-015).
        put(TENANT_ALFA_ID, ANA_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.NO_CONTENT);
        assertThat(isTenantAdminFor(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)).isTrue();

        // Idempotent grant.
        put(TENANT_ALFA_ID, ANA_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.NO_CONTENT);

        // Revoke of a *different* member (bruno revokes ana's): ana's own next request already
        // reflects the loss (SC-010).
        delete(TENANT_ALFA_ID, ANA_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.NO_CONTENT);
        assertThat(isTenantAdminFor(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)).isFalse();

        // Idempotent revoke.
        delete(TENANT_ALFA_ID, ANA_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.NO_CONTENT);
    }

    @Test
    void grantToNonMemberReturnsNotFoundWithoutCreatingMembership() {
        // admin (User 3 - ADMIN) holds no membership in Tenant 1 at all.
        put(TENANT_ALFA_ID, ADMIN_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.NOT_FOUND);

        ResponseEntity<List> members =
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID + "/members",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(members.getBody()).noneMatch(m -> ADMIN_ID.equals(((Map<?, ?>) m).get("userId")));
    }

    @Test
    void selfRevokeSucceedsWithNoLastAdminProtection() {
        delete(TENANT_ALFA_ID, BRUNO_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.NO_CONTENT);
        try {
            assertThat(isTenantAdminFor(BRUNO_USERNAME, BRUNO_PASSWORD, TENANT_ALFA_ID)).isFalse();
        } finally {
            // Restore the seed baseline via System Admin, since bruno can no longer do it himself.
            put(TENANT_ALFA_ID, BRUNO_ID, ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);
        }
    }

    @Test
    void grantRevokeAgainstAnotherTenantIsDenied() {
        // bruno (User 2 - TENANT ADMIN) has no standing at all in Tenant 2.
        put(TENANT_BETA_ID, ANA_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.FORBIDDEN);
        delete(TENANT_BETA_ID, ANA_ID, BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.FORBIDDEN);
    }

    private boolean isTenantAdminFor(String username, String password, String tenantId) {
        ResponseEntity<Map> me =
                restTemplate.exchange(
                        "/api/me",
                        HttpMethod.GET,
                        entity(null, authHeaders(username, password, null)),
                        Map.class);
        List<Map<String, Object>> tenants = (List<Map<String, Object>>) me.getBody().get("tenants");
        return tenants.stream()
                .filter(t -> tenantId.equals(t.get("id")))
                .anyMatch(t -> Boolean.TRUE.equals(t.get("isTenantAdmin")));
    }

    private void put(String tenantId, String userId, String username, String password, HttpStatus expected) {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/tenants/" + tenantId + "/members/" + userId + "/tenant-admin",
                        HttpMethod.PUT,
                        entity(null, authHeaders(username, password, null)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }

    private void delete(String tenantId, String userId, String username, String password, HttpStatus expected) {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/tenants/" + tenantId + "/members/" + userId + "/tenant-admin",
                        HttpMethod.DELETE,
                        entity(null, authHeaders(username, password, null)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }
}
