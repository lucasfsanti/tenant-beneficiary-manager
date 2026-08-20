package com.tbm.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.tenant.TenantRepository;
import com.tbm.user.UserTenantMembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
                new TenantContextFilter(
                        mock(UserTenantMembershipRepository.class),
                        new ObjectMapper(),
                        mock(TenantAccessAuditLogRepository.class),
                        mock(TenantRepository.class));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/beneficiarios");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void savesAnAuditRecordWhenASystemAdminUsesTheCrossTenantBypass() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        UUID targetTenantId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new JwtService.JwtPrincipal(adminUserId, "admin", List.of()),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));

        UserTenantMembershipRepository membershipRepository =
                mock(UserTenantMembershipRepository.class);
        when(membershipRepository.existsByUser_IdAndTenant_Id(adminUserId, targetTenantId))
                .thenReturn(false);
        TenantAccessAuditLogRepository auditLogRepository =
                mock(TenantAccessAuditLogRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.existsById(targetTenantId)).thenReturn(true);

        TenantContextFilter filter =
                new TenantContextFilter(
                        membershipRepository, new ObjectMapper(), auditLogRepository, tenantRepository);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/beneficiarios");
        when(request.getHeader("X-Tenant-Id")).thenReturn(targetTenantId.toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        ArgumentCaptor<TenantAccessAuditLog> captor =
                ArgumentCaptor.forClass(TenantAccessAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        TenantAccessAuditLog saved = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(saved.getAdminUserId()).isEqualTo(adminUserId);
        org.assertj.core.api.Assertions.assertThat(saved.getTargetTenantId())
                .isEqualTo(targetTenantId);
    }

    @Test
    void doesNotSaveAnAuditRecordForAnOrdinaryMemberRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new JwtService.JwtPrincipal(userId, "user", List.of(tenantId)),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        UserTenantMembershipRepository membershipRepository =
                mock(UserTenantMembershipRepository.class);
        when(membershipRepository.existsByUser_IdAndTenant_Id(userId, tenantId)).thenReturn(true);
        TenantAccessAuditLogRepository auditLogRepository =
                mock(TenantAccessAuditLogRepository.class);

        TenantContextFilter filter =
                new TenantContextFilter(
                        membershipRepository,
                        new ObjectMapper(),
                        auditLogRepository,
                        mock(TenantRepository.class));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/beneficiarios");
        when(request.getHeader("X-Tenant-Id")).thenReturn(tenantId.toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(auditLogRepository, never()).save(any());
    }

    /** Convergence finding T027: without this check, the bypass path would let the audit-log
     * INSERT hit an uncaught FK violation for a tenant id that doesn't exist. */
    @Test
    void rejectsTheBypassWithA404WhenTheTargetTenantDoesNotExist() throws Exception {
        UUID adminUserId = UUID.randomUUID();
        UUID nonExistentTenantId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new JwtService.JwtPrincipal(adminUserId, "admin", List.of()),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));

        UserTenantMembershipRepository membershipRepository =
                mock(UserTenantMembershipRepository.class);
        when(membershipRepository.existsByUser_IdAndTenant_Id(adminUserId, nonExistentTenantId))
                .thenReturn(false);
        TenantAccessAuditLogRepository auditLogRepository =
                mock(TenantAccessAuditLogRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.existsById(nonExistentTenantId)).thenReturn(false);

        TenantContextFilter filter =
                new TenantContextFilter(
                        membershipRepository, new ObjectMapper(), auditLogRepository, tenantRepository);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        java.io.StringWriter body = new java.io.StringWriter();
        when(response.getWriter()).thenReturn(new java.io.PrintWriter(body));
        FilterChain filterChain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/beneficiarios");
        when(request.getHeader("X-Tenant-Id")).thenReturn(nonExistentTenantId.toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(response).setStatus(org.springframework.http.HttpStatus.NOT_FOUND.value());
        verify(auditLogRepository, never()).save(any());
        org.assertj.core.api.Assertions.assertThat(body.toString()).contains("não existe");
    }
}
