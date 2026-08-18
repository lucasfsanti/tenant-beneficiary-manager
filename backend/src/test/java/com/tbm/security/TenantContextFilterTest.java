package com.tbm.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.user.UserTenantMembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Covers a branch unreachable through the app's real request flow: JwtAuthenticationFilter is the
 * only component that ever populates the SecurityContext in this app, and it always does so with
 * a {@link JwtService.JwtPrincipal}. This test constructs that otherwise-impossible state directly
 * to verify the filter still degrades safely (passes through, letting Spring Security's own rules
 * reject downstream) rather than throwing a ClassCastException.
 */
class TenantContextFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesThroughWhenThePrincipalIsNotAJwtPrincipal() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("not-a-jwt-principal", null));

        TenantContextFilter filter =
                new TenantContextFilter(mock(UserTenantMembershipRepository.class), new ObjectMapper());

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/beneficiarios");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
