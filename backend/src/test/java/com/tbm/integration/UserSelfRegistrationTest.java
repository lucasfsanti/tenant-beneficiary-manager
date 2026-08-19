package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Extends {@link AbstractIntegrationTest} — its shared, always-demo-seeded container is exactly
 * the right fixture for spec 006-user-self-registration's User Story 2: every test here runs
 * against a platform that already has accounts, which is the scenario this story covers
 * (registering against a genuinely empty platform instead needs
 * {@link UserSelfRegistrationBootstrapTest}'s own isolated container).
 */
class UserSelfRegistrationTest extends AbstractIntegrationTest {

    private ResponseEntity<Map> register(Object body) {
        return restTemplate.postForEntity("/api/auth/register", body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String username, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) response.getBody().get("user");
    }

    @Test
    void registeringAgainstAnAlreadyPopulatedPlatformYieldsANormalAccountWithNoTenants() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "newly-registered-normal");
        body.put("password", "a-real-password");

        ResponseEntity<Map> response = register(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> user = login("newly-registered-normal", "a-real-password");
        assertThat(user.get("isSystemAdmin")).isEqualTo(false);
        assertThat((List<?>) user.get("tenants")).isEmpty();
    }

    @Test
    void registeringWithAnAlreadyTakenUsernameIsRejected() {
        Map<String, String> body = new HashMap<>();
        body.put("username", ANA_USERNAME);
        body.put("password", "irrelevant");

        ResponseEntity<Map> response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void registeringWithABlankUsernameIsRejected() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "");
        body.put("password", "a-real-password");

        ResponseEntity<Map> response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registeringWithABlankPasswordIsRejected() {
        Map<String, String> body = new HashMap<>();
        body.put("username", "someone-new");
        body.put("password", "");

        ResponseEntity<Map> response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** spec FR-011, Edge Cases, US3/AC1: extra fields suggesting elevated access are simply not
     * part of RegisterRequest's shape and must be silently dropped during deserialization — the
     * resulting account must still come out Normal, on an already-populated platform. */
    @Test
    void extraFieldsSuggestingElevatedAccessAreIgnored() {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "attempted-admin");
        body.put("password", "whatever");
        body.put("isSystemAdmin", true);
        body.put("role", "ADMIN");

        ResponseEntity<Map> response = register(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> user = login("attempted-admin", "whatever");
        assertThat(user.get("isSystemAdmin")).isEqualTo(false);
    }
}
