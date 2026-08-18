package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import com.tbm.pessoa.dto.PessoaInput;
import com.tbm.pessoa.dto.PessoaResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Confirms FR-007/FR-010: role tier is purely additive for a Normal-tier user. */
class NormalUserRoleBaselineTest extends AbstractIntegrationTest {

    private static final String PESSOA_ID = "55555555-5555-5555-5555-555555555554";

    @Test
    void existingPessoaAndBeneficiarioCrudIsUnchanged() {
        // CPF is a syntactically valid, check-digit-valid Brazilian CPF not used elsewhere.
        PessoaInput pessoaInput = new PessoaInput("Baseline Test", "35228420304", null, null);

        ResponseEntity<PessoaResponse> pessoaCreate =
                restTemplate.exchange(
                        "/api/pessoas",
                        HttpMethod.POST,
                        entity(pessoaInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        PessoaResponse.class);
        assertThat(pessoaCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID newPessoaId = pessoaCreate.getBody().id();

        BeneficiarioInput beneficiarioInput =
                new BeneficiarioInput(
                        UUID.fromString(PESSOA_ID),
                        "MAT-BASELINE-001",
                        BeneficiarioTipo.TITULAR,
                        BeneficiarioStatus.ATIVO,
                        null);
        ResponseEntity<BeneficiarioResponse> beneficiarioCreate =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.POST,
                        entity(beneficiarioInput, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                        BeneficiarioResponse.class);
        assertThat(beneficiarioCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Cleanup.
        restTemplate.exchange(
                "/api/beneficiarios/" + beneficiarioCreate.getBody().id(),
                HttpMethod.DELETE,
                entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, TENANT_ALFA_ID)),
                Void.class);
        restTemplate.exchange(
                "/api/pessoas/" + newPessoaId,
                HttpMethod.DELETE,
                entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                Void.class);
    }

    @Test
    void normalTierUserIsDeniedEveryNewTenantAndMembershipEndpoint() {
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.POST,
                        entity(Map.of("name", "x"), authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID,
                        HttpMethod.PUT,
                        entity(Map.of("name", "x"), authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID,
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID + "/members",
                        HttpMethod.POST,
                        entity(
                                Map.of("userId", "77777777-7777-7777-7777-777777777777"),
                                authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/" + TENANT_ALFA_ID + "/members/44444444-4444-4444-4444-444444444444",
                        HttpMethod.DELETE,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
        assertForbidden(
                restTemplate.exchange(
                        "/api/tenants/"
                                + TENANT_ALFA_ID
                                + "/members/44444444-4444-4444-4444-444444444444/tenant-admin",
                        HttpMethod.PUT,
                        entity(null, authHeaders(ANA_USERNAME, ANA_PASSWORD, null)),
                        Map.class));
    }

    private void assertForbidden(ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
