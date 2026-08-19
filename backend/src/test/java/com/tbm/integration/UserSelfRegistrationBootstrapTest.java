package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Deliberately does NOT extend {@link AbstractIntegrationTest}: that class's shared, JVM-lifetime
 * Postgres container never sets {@code @ActiveProfiles}, so it always boots with the {@code demo}
 * profile and Liquibase always seeds demo data before any test method runs — meaning {@code
 * app_user} is never genuinely empty in that shared container. Self-registration's bootstrap
 * behavior (spec 006-user-self-registration FR-003, Edge Cases' race condition) can only be
 * observed against a database that starts empty, so this class gets its own, class-scoped
 * container with the {@code no-demo} profile active (feature 005), leaving {@code app_user}
 * empty until this class's own tests populate it.
 *
 * <p>{@code app_user} genuinely starts empty only ONCE for this whole class — the first test
 * method to run consumes that moment, and every method after it necessarily runs against an
 * already-non-empty database (no rollback happens between methods for a real-HTTP integration
 * test like this one). {@link MethodOrderer.OrderAnnotation} makes that explicit instead of
 * relying on JUnit's unspecified default order: the concurrency race (T015) needs genuine
 * emptiness for its core assertion and runs first; the sequential bootstrap check runs second,
 * deliberately re-scoped to only claim what's still true once the database already has the two
 * racers' accounts in it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("no-demo")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserSelfRegistrationBootstrapTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("tbm")
                    .withUsername("tbm")
                    .withPassword("tbm");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private TestRestTemplate restTemplate;

    private ResponseEntity<Void> register(String username, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return restTemplate.postForEntity("/api/auth/register", body, Void.class);
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

    /**
     * Edge Cases' race condition, US3/AC2: fires two registrations at nearly the same instant
     * against the still-genuinely-empty database, then asserts exactly one of the two ended up
     * System Admin — never both, never neither. Directly exercises research.md §1's advisory lock
     * under real contention (a Mockito-based unit test cannot meaningfully test real database lock
     * behavior).
     */
    @Test
    @Order(1)
    void concurrentRegistrationsAgainstAnEmptyPlatformYieldExactlyOneSystemAdmin() {
        CompletableFuture<ResponseEntity<Void>> racerA =
                CompletableFuture.supplyAsync(() -> register("racer-a", "password-a"));
        CompletableFuture<ResponseEntity<Void>> racerB =
                CompletableFuture.supplyAsync(() -> register("racer-b", "password-b"));
        CompletableFuture.allOf(racerA, racerB).join();

        assertThat(racerA.join().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(racerB.join().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        boolean aIsAdmin = (boolean) login("racer-a", "password-a").get("isSystemAdmin");
        boolean bIsAdmin = (boolean) login("racer-b", "password-b").get("isSystemAdmin");

        assertThat(aIsAdmin ^ bIsAdmin)
                .as("exactly one of the two concurrent registrations should become System Admin")
                .isTrue();
    }

    /**
     * By the time this runs, {@code app_user} already holds the two racers from {@link
     * #concurrentRegistrationsAgainstAnEmptyPlatformYieldExactlyOneSystemAdmin()} — so this
     * verifies FR-005 ("every account after the first is Normal") rather than re-claiming to be
     * the platform's very first account, which that other test already consumed.
     */
    @Test
    @Order(2)
    void aRegistrationAfterTheRaceIsStillNormal() {
        ResponseEntity<Void> response = register("third-person", "another-password");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> user = login("third-person", "another-password");
        assertThat(user.get("isSystemAdmin")).isEqualTo(false);
        assertThat((List<?>) user.get("tenants")).isEmpty();
    }
}
