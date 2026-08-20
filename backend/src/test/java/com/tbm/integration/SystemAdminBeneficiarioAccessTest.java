package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.beneficiario.BeneficiarioStatus;
import com.tbm.beneficiario.BeneficiarioTipo;
import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import com.tbm.security.TenantAccessAuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** Seeded (004-role-system-seed-data.sql) platform-wide System Admin's user id. */
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777777");

    /** Tenant 3 — User 1 - NORMAL holds no membership here (only Tenant Alfa/Beta). */
    private static final String TENANT_WITHOUT_MEMBERSHIP_ID =
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1";

    @Autowired private TenantAccessAuditLogRepository auditLogRepository;

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

    /** FR-013/SC-006: every successful use of the cross-tenant bypass is audited. */
    @Test
    void systemAdminCrossTenantAccessProducesAnAuditRecord() {
        long before = auditLogRepository.count();

        ResponseEntity<Map> listResponse =
                restTemplate.exchange(
                        "/api/beneficiarios",
                        HttpMethod.GET,
                        entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, TENANT_ALFA_ID)),
                        Map.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        var latest =
                auditLogRepository.findAll().stream()
                        .max(java.util.Comparator.comparing(
                                com.tbm.security.TenantAccessAuditLog::getAccessedAt))
                        .orElseThrow();
        assertThat(latest.getAdminUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(latest.getTargetTenantId()).isEqualTo(UUID.fromString(TENANT_ALFA_ID));
    }

    /** SC-005/SC-006: a non-admin's rejected cross-tenant attempt is neither granted nor
     * audited — only a granted System Admin bypass produces a record. */
    @Test
    void rejectedNonAdminCrossTenantAttemptProducesNoAuditRecord() {
        long before = auditLogRepository.count();

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

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    /** Coverage gap (spec 008-test-coverage-tracking, research.md §3): a System Admin who is
     * ALSO a genuine member of the target tenant is not exercising the bypass at all, so this
     * must not be audited — distinct from {@link #systemAdminCrossTenantAccessProducesAnAuditRecord}. */
    @Test
    void systemAdminWhoIsAlsoAGenuineMemberProducesNoAuditRecord() {
        ResponseEntity<Map> createTenantResponse =
                restTemplate.exchange(
                        "/api/tenants",
                        HttpMethod.POST,
                        entity(
                                Map.of("name", "Admin Membership Coverage Test " + UUID.randomUUID()),
                                authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                        Map.class);
        assertThat(createTenantResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tenantId = (String) createTenantResponse.getBody().get("id");

        try {
            ResponseEntity<Map> addMemberResponse =
                    restTemplate.exchange(
                            "/api/tenants/" + tenantId + "/members",
                            HttpMethod.POST,
                            entity(
                                    Map.of("userId", ADMIN_USER_ID.toString()),
                                    authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                            Map.class);
            assertThat(addMemberResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            long before = auditLogRepository.count();

            ResponseEntity<Map> listResponse =
                    restTemplate.exchange(
                            "/api/beneficiarios",
                            HttpMethod.GET,
                            entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, tenantId)),
                            Map.class);
            assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(auditLogRepository.count()).isEqualTo(before);
        } finally {
            restTemplate.exchange(
                    "/api/tenants/" + tenantId + "/members/" + ADMIN_USER_ID,
                    HttpMethod.DELETE,
                    entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                    Void.class);
            restTemplate.exchange(
                    "/api/tenants/" + tenantId,
                    HttpMethod.DELETE,
                    entity(null, authHeaders(ADMIN_USERNAME, ADMIN_PASSWORD, null)),
                    Void.class);
        }
    }
}
