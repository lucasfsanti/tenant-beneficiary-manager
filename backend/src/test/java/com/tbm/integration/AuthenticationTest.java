package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

class AuthenticationTest extends AbstractIntegrationTest {

    @Test
    void rejectsLoginWithWrongPassword() {
        ResponseEntity<Map> response = login(ANA_USERNAME, "not-the-real-password");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsLoginWithNonexistentUsername() {
        ResponseEntity<Map> response = login("nao-existe", "qualquer");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The JDK's default HttpURLConnection-based client factory cannot cleanly surface a 401
     * response to a POST made with a streamed request body ("cannot retry due to server
     * authentication, in streaming mode" — a long-standing HttpURLConnection quirk, not a server
     * bug). The java.net.http.HttpClient-backed factory doesn't have this issue.
     */
    private ResponseEntity<Map> login(String username, String password) {
        RestTemplate template = restTemplate.getRestTemplate();
        ClientHttpRequestFactory original = template.getRequestFactory();
        template.setRequestFactory(new JdkClientHttpRequestFactory());
        try {
            return restTemplate.postForEntity(
                    "/api/auth/login", Map.of("username", username, "password", password), Map.class);
        } finally {
            template.setRequestFactory(original);
        }
    }

    @Test
    void rejectsRequestWithNoAuthorizationHeader() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/me", HttpMethod.GET, entity(null, new HttpHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRequestWithAnAuthorizationHeaderNotUsingTheBearerScheme() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic dXNlcjpwYXNz");
        ResponseEntity<Map> response =
                restTemplate.exchange("/api/me", HttpMethod.GET, entity(null, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRequestWithInvalidToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this-is-not-a-valid-jwt");
        ResponseEntity<Map> response =
                restTemplate.exchange("/api/me", HttpMethod.GET, entity(null, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
