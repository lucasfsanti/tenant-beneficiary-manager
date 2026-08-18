package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers FR-016: looking up an existing user by exact username (used to reference them when adding a membership). */
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
                        "/api/users?username=nao-existe-de-verdade",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }
}
