package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.pessoa.dto.PessoaInput;
import com.tbm.pessoa.dto.PessoaResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PessoaDeletionRestrictionTest extends AbstractIntegrationTest {

    /** Seeded Pessoa ("Pessoa 1" as of 005-seed-data-relabel-and-expand.sql) referenced by
     * Beneficiario rows in both Tenant 1 and Tenant 2. */
    private static final String REFERENCED_PESSOA_ID = "55555555-5555-5555-5555-555555555551";

    @Test
    void deletesSuccessfullyWhenNotLinkedToAnyBeneficiario() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAndGetToken(ANA_USERNAME, ANA_PASSWORD));

        PessoaInput input = new PessoaInput("Sem Vinculo", "16899535009", null, null);
        ResponseEntity<PessoaResponse> createResponse =
                restTemplate.exchange(
                        "/api/pessoas", HttpMethod.POST, entity(input, headers), PessoaResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = createResponse.getBody().id().toString();

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange(
                        "/api/pessoas/" + id, HttpMethod.DELETE, entity(null, headers), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse =
                restTemplate.exchange(
                        "/api/pessoas/" + id, HttpMethod.GET, entity(null, headers), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void blocksDeletionWithGenericConflictMessage() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAndGetToken(ANA_USERNAME, ANA_PASSWORD));

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/pessoas/" + REFERENCED_PESSOA_ID,
                        HttpMethod.DELETE,
                        entity(null, headers),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String detail = (String) response.getBody().get("detail");
        assertThat(detail).isNotBlank();
        assertThat(detail.toLowerCase()).doesNotContain("tenant 1");
        assertThat(detail.toLowerCase()).doesNotContain("tenant 2");
        assertThat(detail).doesNotContain("11111111-1111-1111-1111-111111111111");
        assertThat(detail).doesNotContain("22222222-2222-2222-2222-222222222222");
    }
}
