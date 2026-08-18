package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Validates FR-011's atomicity requirement (research.md §9). */
class SystemAdminConcurrentRevokeTest extends AbstractIntegrationTest {

    private static final String ANA_ID = "33333333-3333-3333-3333-333333333333";
    private static final String ADMIN_ID = "77777777-7777-7777-7777-777777777777";

    @Test
    void exactlyOneOfTwoConcurrentRevokesAgainstTheLastTwoAdminsSucceeds() throws Exception {
        // Promote ana so exactly two System Admins exist: admin and ana.
        restTemplate.exchange(
                "/api/users/" + ANA_ID + "/system-admin",
                HttpMethod.PUT,
                entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                Void.class);
        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch startTogether = new CountDownLatch(1);

            Future<HttpStatus> revokeAdmin =
                    pool.submit(() -> revoke(ADMIN_ID, ANA_USERNAME, ANA_PASSWORD, startTogether));
            Future<HttpStatus> revokeAna =
                    pool.submit(() -> revoke(ANA_ID, ADMIN_USERNAME, ADMIN_PASSWORD, startTogether));

            startTogether.countDown();
            HttpStatus resultA = revokeAdmin.get(10, TimeUnit.SECONDS);
            HttpStatus resultB = revokeAna.get(10, TimeUnit.SECONDS);
            pool.shutdown();

            long successes =
                    java.util.stream.Stream.of(resultA, resultB)
                            .filter(s -> s == HttpStatus.NO_CONTENT)
                            .count();
            assertThat(successes).isEqualTo(1);

            long remainingSystemAdmins =
                    java.util.stream.Stream.of(
                                    isStillSystemAdmin(ADMIN_USERNAME, ADMIN_PASSWORD),
                                    isStillSystemAdmin(ANA_USERNAME, ANA_PASSWORD))
                            .filter(Boolean::booleanValue)
                            .count();
            assertThat(remainingSystemAdmins).isEqualTo(1);
        } finally {
            // Restore the seed baseline: admin is System Admin, ana is not. Whichever of the two
            // survived the concurrent revoke above performs the restore.
            org.springframework.http.HttpHeaders survivorHeaders = adminOrAnaHeaders();
            restTemplate.exchange(
                    "/api/users/" + ADMIN_ID + "/system-admin",
                    HttpMethod.PUT,
                    entity(null, survivorHeaders),
                    Void.class);
            restTemplate.exchange(
                    "/api/users/" + ANA_ID + "/system-admin",
                    HttpMethod.DELETE,
                    entity(null, survivorHeaders),
                    Void.class);
        }
    }

    private org.springframework.http.HttpHeaders adminOrAnaHeaders() {
        // Whichever of the two still holds System Admin standing can perform the restore.
        return isStillSystemAdmin(ADMIN_USERNAME, ADMIN_PASSWORD)
                ? authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)
                : authHeaders(ANA_USERNAME, ANA_PASSWORD, null);
    }

    private HttpStatus revoke(String userId, String username, String password, CountDownLatch startTogether) {
        try {
            startTogether.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/users/" + userId + "/system-admin",
                        HttpMethod.DELETE,
                        entity(null, authHeaders(username, password, null)),
                        Map.class);
        return (HttpStatus) response.getStatusCode();
    }

    private boolean isStillSystemAdmin(String username, String password) {
        ResponseEntity<Map> me =
                restTemplate.exchange(
                        "/api/me",
                        HttpMethod.GET,
                        entity(null, authHeaders(username, password, null)),
                        Map.class);
        return Boolean.TRUE.equals(me.getBody().get("isSystemAdmin"));
    }
}
