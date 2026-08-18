package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BeneficiarioFilteringTest extends AbstractIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void filtersByPessoaNome() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios?pessoaNome=Maria",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allMatch(item -> ((String) item.get("pessoaNome")).contains("Maria"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersByStatus() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios?status=ATIVO",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allMatch(item -> "ATIVO".equals(item.get("status")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void combinesNameAndStatusFilters() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios?pessoaNome=Maria&status=ATIVO",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content)
                .allMatch(
                        item ->
                                ((String) item.get("pessoaNome")).contains("Maria")
                                        && "ATIVO".equals(item.get("status")));
    }

    @Test
    void pageBeyondLastReturnsEmptyNotError() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios?page=999&size=20",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("content")).isEmpty();
    }
}
