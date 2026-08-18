package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantSwitchRoundTripTest extends AbstractIntegrationTest {

    @Test
    void listingIsUnchangedAfterSwitchingAwayAndBack() {
        ResponseEntity<Map> before =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> otherTenant =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_BETA_ID)),
                        Map.class);
        assertThat(otherTenant.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> after =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody()).isEqualTo(before.getBody());
    }
}
