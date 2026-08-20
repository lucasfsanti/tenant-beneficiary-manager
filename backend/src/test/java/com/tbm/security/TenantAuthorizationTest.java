package com.tbm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.tbm.user.UserTenantMembershipRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Covers {@link TenantAuthorization}'s defensive branch — unreachable via a real HTTP request,
 * since {@code @PreAuthorize} SpEL is only ever evaluated after {@code JwtAuthenticationFilter}
 * has already populated the SecurityContext with a {@link JwtService.JwtPrincipal} — mirroring
 * the same precedent already covered for {@link TenantContextFilter} in
 * {@code TenantContextFilterTest.passesThroughWhenThePrincipalIsNotAJwtPrincipal}.
 */
class TenantAuthorizationTest {

    private final TenantAuthorization tenantAuthorization =
            new TenantAuthorization(mock(UserTenantMembershipRepository.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsFalseWhenThereIsNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(tenantAuthorization.isTenantAdmin(UUID.randomUUID())).isFalse();
    }

    @Test
    void returnsFalseWhenThePrincipalIsNotAJwtPrincipal() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("not-a-jwt-principal", null));

        assertThat(tenantAuthorization.isTenantAdmin(UUID.randomUUID())).isFalse();
    }
}
