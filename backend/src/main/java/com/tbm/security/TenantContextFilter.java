package com.tbm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.user.UserTenantMembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves and validates the active tenant for every {@code /api/beneficiarios/**} request,
 * before any repository access — Constitution Principle I's single centralized enforcement
 * point. Rejects with 403 if {@code X-Tenant-Id} is not one of the caller's own memberships.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final UserTenantMembershipRepository membershipRepository;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(
            UserTenantMembershipRepository membershipRepository, ObjectMapper objectMapper) {
        this.membershipRepository = membershipRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        boolean tenantScoped = request.getRequestURI().startsWith("/api/beneficiarios");
        if (!tenantScoped) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof JwtService.JwtPrincipal principal)) {
            // Not authenticated yet — let Spring Security's authorization rules produce a 401.
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(TENANT_HEADER);
        if (header == null || header.isBlank()) {
            writeProblem(
                    response, HttpStatus.BAD_REQUEST, "O cabeçalho X-Tenant-Id é obrigatório.");
            return;
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "O cabeçalho X-Tenant-Id é inválido.");
            return;
        }

        if (!membershipRepository.existsByUser_IdAndTenant_Id(principal.userId(), tenantId)) {
            writeProblem(
                    response,
                    HttpStatus.FORBIDDEN,
                    "Você não tem acesso ao tenant informado em X-Tenant-Id.");
            return;
        }

        try {
            TenantContext.set(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status == HttpStatus.FORBIDDEN ? "Acesso negado" : "Requisição inválida");
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
