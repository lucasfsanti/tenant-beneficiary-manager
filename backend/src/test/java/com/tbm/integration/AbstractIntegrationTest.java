package com.tbm.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    /** Seeded (V2__seed_demo_data.sql) multi-tenant demo user. */
    protected static final String ANA_USERNAME = "ana";

    protected static final String ANA_PASSWORD = "demo123";
    protected static final String TENANT_ALFA_ID = "11111111-1111-1111-1111-111111111111";
    protected static final String TENANT_BETA_ID = "22222222-2222-2222-2222-222222222222";

    /**
     * Deliberately NOT annotated with {@code @Testcontainers}/{@code @Container}: that combo
     * scopes the container's start/stop lifecycle to each individual test class (JUnit5
     * extension store is per-class), so a static field would still be stopped and restarted
     * from scratch for every one of the ~12 integration test classes that extend this class.
     * This is Testcontainers' documented "singleton container" pattern instead — started once,
     * shared for the whole JVM/test run, and left for Ryuk to reap at JVM exit.
     */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16").withDatabaseName("tbm").withUsername("tbm").withPassword("tbm");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String loginAndGetToken(String username, String password) {
        var body = new java.util.HashMap<String, String>();
        body.put("username", username);
        body.put("password", password);
        var response =
                restTemplate.postForEntity("/api/auth/login", body, java.util.Map.class);
        return (String) response.getBody().get("token");
    }

    protected HttpHeaders authHeaders(String username, String password, String tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAndGetToken(username, password));
        if (tenantId != null) {
            headers.set("X-Tenant-Id", tenantId);
        }
        return headers;
    }

    protected <T> HttpEntity<T> entity(T body, HttpHeaders headers) {
        return new HttpEntity<>(body, headers);
    }
}
