package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Regression test for a convergence finding: FR-008 requires a System Admin to inherit every
 * Normal-tier action (including Beneficiário CRUD) across every tenant, regardless of whether
 * they hold a membership row there — but {@code admin} (the seeded System Admin) holds none.
 */
class SystemAdminBeneficiarioAccessTest extends AbstractIntegrationTest {

    private static final String PESSOA_ID = "55555555-5555-5555-5555-555555555554";

    @Test
    void systemAdminWithNoMembershipCanManageBeneficiariosInAnyTenant() {
        ResponseEntity<Map> listResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        BeneficiarioInput createInput =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-SYSADMIN-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> createResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(createInput, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = createResponse.getBody().id();

        ResponseEntity<BeneficiarioResponse> getResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.GET,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        BeneficiarioInput updateInput =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-SYSADMIN-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.INATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> updateResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.PUT,
                        entity(updateInput, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().status()).isEqualTo(BeneficiarioStatus.INATIVO);

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange(
                        "/api/beneficiarios/" + id,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, TENANT_ALFA_ID)),
                        Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
