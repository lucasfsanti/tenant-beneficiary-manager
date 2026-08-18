package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PessoaDeletionRestrictionTest extends AbstractIntegrationTest {

    /** Seeded Pessoa (V2__seed_demo_data.sql) referenced by Beneficiario rows in both tenants. */
    private static final String REFERENCED_PESSOA_ID = "55555555-5555-5555-5555-555555555551";

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
        assertThat(detail.toLowerCase()).doesNotContain("tenant alfa");
        assertThat(detail.toLowerCase()).doesNotContain("tenant beta");
        assertThat(detail).doesNotContain("11111111-1111-1111-1111-111111111111");
        assertThat(detail).doesNotContain("22222222-2222-2222-2222-222222222222");
    }
}
