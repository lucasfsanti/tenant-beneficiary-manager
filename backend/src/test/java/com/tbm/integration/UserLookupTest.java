package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers FR-016 (exact-username lookup, still supported since an exact username is itself a
 * valid substring) and spec 009-searchable-select-filters FR-003/SC-005 (substring matching,
 * case-insensitivity, the 2-character minimum, and the 20-result cap).
 */
class UserLookupTest extends AbstractIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void findsExistingUserByExactUsername() {
        ResponseEntity<List> response =
                restTemplate.exchange(
                        "/api/users?username=" + ANA_USERNAME,
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("username")).isEqualTo(ANA_USERNAME);
    }

    @Test
    void returnsEmptyListWhenUsernameNotFound() {
        ResponseEntity<List> response =
                restTemplate.exchange(
                        "/api/users?username=nao-existe-de-verdade-zzz",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchesAFragmentFromTheMiddleOfAUsernameNotJustItsStart() {
        // ANA_USERNAME is "User 1 - NORMAL" — "1 - NOR" is neither the start nor the whole value.
        ResponseEntity<List> response =
                restTemplate.exchange(
                        "/api/users?username=1 - NOR",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = response.getBody();
        assertThat(body).extracting(u -> u.get("username")).contains(ANA_USERNAME);
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchingIsCaseInsensitive() {
        ResponseEntity<List> response =
                restTemplate.exchange(
                        "/api/users?username=user 1 - normal",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = response.getBody();
        assertThat(body).extracting(u -> u.get("username")).contains(ANA_USERNAME);
    }

    @Test
    void returnsEmptyListWithoutMatchingAnythingWhenSearchIsShorterThanTwoCharacters() {
        ResponseEntity<List> response =
                restTemplate.exchange(
                        "/api/users?username=U",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void capsResultsAtTwentyWhenMoreThanTwentyUsersMatch() {
        String marker = "CapTest" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 21; i++) {
            var registerBody = Map.of("username", marker + "-" + i, "password", "demo123");
            ResponseEntity<Void> registerResponse =
                    restTemplate.postForEntity("/api/auth/register", registerBody, Void.class);
            assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        ResponseEntity<List> response =
                restTemplate.exchange(
                        "/api/users?username=" + marker,
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(20);
    }
}
