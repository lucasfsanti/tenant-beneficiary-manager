package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantIsolationTest extends AbstractIntegrationTest {

    /** Seeded Beneficiario ("Beneficiário 1 - Tenant 2" as of 005-seed-data-relabel-and-expand.sql)
     * that belongs to Tenant 2, not Tenant 1. */
    private static final String TENANT_BETA_BENEFICIARIO_ID = "66666666-6666-6666-6666-666666666663";

    /** Tenant 3 (005-seed-data-relabel-and-expand.sql) — User 1 - NORMAL holds no membership
     * here (only Tenant Alfa/Beta). Used by the SC-004 case below (spec
     * 007-tenant-transparent-views): a selector for a tenant the user isn't a member of at all,
     * as opposed to the tests above, which use a tenant the user *is* a member of but that isn't
     * the record's tenant. */
    private static final String TENANT_WITHOUT_MEMBERSHIP_ID =
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1";

    @Test
    void returns404ForRecordBelongingToAnotherTenant() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios/" + TENANT_BETA_BENEFICIARIO_ID,
                        HttpMethod.GET,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenEditingARecordBelongingToAnotherTenant() {
        Map<String, Object> updateInput =
                Map.of(
                        "pessoaId", "55555555-5555-5555-5555-555555555553",
                        "matricula", "Beneficiário 1 - Tenant 2",
                        "tipo", "TITULAR",
                        "status", "INATIVO");
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios/" + TENANT_BETA_BENEFICIARIO_ID,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenDeletingARecordBelongingToAnotherTenant() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios/" + TENANT_BETA_BENEFICIARIO_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** SC-004 (spec 007-tenant-transparent-views): a selector for a tenant the user holds no
     * membership in at all must be rejected outright, distinct from the "member of a different
     * tenant than the record's" 404 cases above. */
    @Test
    void aTenantSelectorForATenantTheUserIsNotAMemberOfIsRejected() {
        ResponseEntity<Map> response =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(
                                null,
                                authHeaders(
                                        ANA_USERNAME, ANA_PASSWORD, TENANT_WITHOUT_MEMBERSHIP_ID)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
