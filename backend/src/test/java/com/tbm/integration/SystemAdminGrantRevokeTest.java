package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SystemAdminGrantRevokeTest extends AbstractIntegrationTest {

    /** Seeded (002-seed-demo-data.sql) Pessoa-unrelated Normal-tier user ("User 1 - NORMAL" as of
     * 005-seed-data-relabel-and-expand.sql), promoted/demoted in these tests. */
    private static final String ANA_ID = "33333333-3333-3333-3333-333333333333";

    private static final String ADMIN_ID = "77777777-7777-7777-7777-777777777777";

    @Test
    void grantTakesEffectImmediatelyAndIsIdempotent() {
        // Grant: ana gains System Admin standing, visible on her very next /api/me call (FR-015).
        put("/api/users/" + ANA_ID + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);
        assertThat(isSystemAdmin(ANA_USERNAME, ANA_PASSWORD)).isTrue();

        // Idempotent: granting already-held standing is a no-op success.
        put("/api/users/" + ANA_ID + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);

        // Revoke by a different admin (admin revokes ana's): ana's own next request already
        // reflects the loss (SC-010), while admin remains a System Admin (more than one exists).
        delete("/api/users/" + ANA_ID + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);
        assertThat(isSystemAdmin(ANA_USERNAME, ANA_PASSWORD)).isFalse();

        // Idempotent: revoking not-held standing is a no-op success.
        delete("/api/users/" + ANA_ID + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);
    }

    @Test
    void lastRemainingSystemAdminCannotBeRevokedEvenBySelf() {
        // admin is (at this point) the sole System Admin.
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/users/" + ADMIN_ID + "/system-admin",
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(isSystemAdmin(ADMIN_USERNAME, ADMIN_PASSWORD)).isTrue();
    }

    @Test
    void revokeSucceedsWhenMoreThanOneSystemAdminExists() {
        put("/api/users/" + ANA_ID + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);
        try {
            // Now two System Admins exist: admin may revoke their own standing safely.
            delete("/api/users/" + ADMIN_ID + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);
            assertThat(isSystemAdmin(ADMIN_USERNAME, ADMIN_PASSWORD)).isFalse();
        } finally {
            // Restore the seed baseline regardless of assertion outcome.
            put("/api/users/" + ADMIN_ID + "/system-admin", ANA_USERNAME, ANA_PASSWORD, HttpStatus.NO_CONTENT);
            delete("/api/users/" + ANA_ID + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NO_CONTENT);
        }
    }

    @Test
    void grantRevokeAgainstNonexistentUserReturnsNotFound() {
        String bogusId = "99999999-9999-9999-9999-999999999999";
        put("/api/users/" + bogusId + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NOT_FOUND);
        delete("/api/users/" + bogusId + "/system-admin", ADMIN_USERNAME, ADMIN_PASSWORD, HttpStatus.NOT_FOUND);
    }

    @Test
    void nonSystemAdminCallerIsDenied() {
        put("/api/users/" + ANA_ID + "/system-admin", BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.FORBIDDEN);
        delete("/api/users/" + ADMIN_ID + "/system-admin", BRUNO_USERNAME, BRUNO_PASSWORD, HttpStatus.FORBIDDEN);
    }

    private boolean isSystemAdmin(String username, String password) {
        ResponseEntity<Map> me =
                restTemplate.exchange(
                        "/api/me",
                        HttpMethod.GET,
                        entity(null, authHeaders(username, password, null)),
                        Map.class);
        return Boolean.TRUE.equals(me.getBody().get("isSystemAdmin"));
    }

    private void put(String path, String username, String password, HttpStatus expected) {
        HttpEntity<Void> request = entity(null, authHeaders(username, password, null));
        ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.PUT, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }

    private void delete(String path, String username, String password, HttpStatus expected) {
        HttpEntity<Void> request = entity(null, authHeaders(username, password, null));
        ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.DELETE, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }
}
