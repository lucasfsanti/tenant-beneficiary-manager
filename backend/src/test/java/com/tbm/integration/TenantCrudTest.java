package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantCrudTest extends AbstractIntegrationTest {

    @Test
    void systemAdminHasFullTenantCrud() {
        Map<String, String> createInput = Map.of("name", "Tenant CRUD Test " + UUID.randomUUID());
        ResponseEntity<Map> createResponse =
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.POST,
                        entity(createInput, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tenantId = (String) createResponse.getBody().get("id");

        ResponseEntity<List> listResponse =
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.GET,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .anyMatch(t -> tenantId.equals(((Map<?, ?>) t).get("id")));

        ResponseEntity<Map> getResponse =
                restTemplate.exchange(
                        "/api/tenants/" + tenantId,
                        HttpMethod.GET,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> updateInput = Map.of("name", "Tenant CRUD Test Renamed " + UUID.randomUUID());
        ResponseEntity<Map> updateResponse =
                restTemplate.exchange(
                        "/api/tenants/" + tenantId,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("name")).isEqualTo(updateInput.get("name"));

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange(
                        "/api/tenants/" + tenantId,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void userWithNoStandingIsDeniedEveryTenantAction() {
        Map<String, String> input = Map.of("name", "Should Not Be Created " + UUID.randomUUID());

        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.POST,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID,
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID,
                        HttpMethod.PUT,
                        entity(input, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
    }

    @Test
    void tenantAdminIsDeniedCreateListAllAndDelete() {
        Map<String, String> input = Map.of("name", "Should Not Be Created " + UUID.randomUUID());

        // bruno (User 2 - TENANT ADMIN) is Tenant Admin of Tenant 1 — still no create/list-all/delete standing.
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.POST,
                        entity(input, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.GET,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(BRUNO_USERNAME, BRUNO_PASSWORD, null)),
                        Map.class));
    }

    @Test
    void getUpdateAndDeleteReturnNotFoundForAnUnknownTenantId() {
        String unknownTenantId = UUID.randomUUID().toString();

        ResponseEntity<Map> getResponse =
                restTemplate.exchange(
                        "/api/tenants/" + unknownTenantId,
                        HttpMethod.GET,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, String> updateInput = Map.of("name", "Should Not Update " + UUID.randomUUID());
        ResponseEntity<Map> updateResponse =
                restTemplate.exchange(
                        "/api/tenants/" + unknownTenantId,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> deleteResponse =
                restTemplate.exchange(
                        "/api/tenants/" + unknownTenantId,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void assertForbidden(ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
