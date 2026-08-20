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
                        "/api/beneficiarios?pessoaNome=Pessoa 1",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allMatch(item -> ((String) item.get("pessoaNome")).contains("Pessoa 1"));
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
                        "/api/beneficiarios?pessoaNome=Pessoa 1&status=ATIVO",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content)
                .allMatch(
                        item ->
                                ((String) item.get("pessoaNome")).contains("Pessoa 1")
                                        && "ATIVO".equals(item.get("status")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void treatsABlankPessoaNomeFilterAsUnfiltered() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios?pessoaNome=",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) response.getBody().get("content")).isNotEmpty();
    }

    @Test
    void filterMatchingNoRecordsShowsEmptyStateNotError() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios?pessoaNome=ZZZ_NOME_INEXISTENTE_ZZZ",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("content")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersByAFragmentFromTheMiddleOfThePessoaNameCaseInsensitively() {
        // Seeded Pessoa names contain "Pessoa N" — "ssoa " is neither the start nor the whole
        // name, and differs in case from the stored value (spec 009-searchable-select-filters
        // FR-002).
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios?pessoaNome=SSOA ",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content)
                .allMatch(item -> ((String) item.get("pessoaNome")).toLowerCase().contains("ssoa "));
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
