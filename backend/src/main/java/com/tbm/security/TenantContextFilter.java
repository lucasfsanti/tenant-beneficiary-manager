package com.tbm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.tenant.TenantRepository;
import com.tbm.user.UserTenantMembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
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
 * point. Rejects with 403 if {@code X-Tenant-Id} is neither one of the caller's own memberships
 * nor the caller holds System Admin standing, which grants every Normal-tier action across every
 * tenant platform-wide regardless of membership (FR-008).
 *
 * <p>System Admin standing is read from the {@code ROLE_SYSTEM_ADMIN} authority already populated
 * on the request's {@link Authentication} by {@link JwtAuthenticationFilter}, rather than this
 * filter re-querying it independently — a single source of truth per request (research.md §5).
 *
 * <p>Every time a System Admin uses this bypass for a tenant they hold no membership in, a
 * {@link TenantAccessAuditLog} row is recorded here — the one place that already evaluates the
 * bypass condition, so there is no second source of truth for "was this a bypass access"
 * (FR-013, research.md §7 of spec 007-tenant-transparent-views). Because
 * {@code tenant_access_audit_log.target_tenant_id} has a {@code NOT NULL REFERENCES tenant(id)}
 * constraint, the bypass path also verifies the target tenant actually exists first — otherwise
 * the audit-log insert would fail with an uncaught {@code ConstraintViolationException} deep in
 * a servlet filter, before {@code ApiExceptionHandler} ever gets a chance to produce a clean
 * error (convergence finding T027).
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String SYSTEM_ADMIN_AUTHORITY = "ROLE_SYSTEM_ADMIN";

    private final UserTenantMembershipRepository membershipRepository;
    private final ObjectMapper objectMapper;
    private final TenantAccessAuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;

    public TenantContextFilter(
            UserTenantMembershipRepository membershipRepository,
            ObjectMapper objectMapper,
            TenantAccessAuditLogRepository auditLogRepository,
            TenantRepository tenantRepository) {
        this.membershipRepository = membershipRepository;
        this.objectMapper = objectMapper;
        this.auditLogRepository = auditLogRepository;
        this.tenantRepository = tenantRepository;
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

        boolean isSystemAdmin =
                authentication.getAuthorities().stream()
                        .anyMatch(authority -> SYSTEM_ADMIN_AUTHORITY.equals(authority.getAuthority()));
        boolean isMember =
                membershipRepository.existsByUser_IdAndTenant_Id(principal.userId(), tenantId);
        if (!isSystemAdmin && !isMember) {
            writeProblem(
                    response,
                    HttpStatus.FORBIDDEN,
                    "Você não tem acesso ao tenant informado em X-Tenant-Id.");
            return;
        }
        if (isSystemAdmin && !isMember) {
            if (!tenantRepository.existsById(tenantId)) {
                writeProblem(
                        response, HttpStatus.NOT_FOUND, "O tenant informado em X-Tenant-Id não existe.");
                return;
            }
            recordCrossTenantAccess(principal.userId(), tenantId);
        }

        try {
            TenantContext.set(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void recordCrossTenantAccess(UUID adminUserId, UUID targetTenantId) {
        TenantAccessAuditLog auditLog = new TenantAccessAuditLog();
        auditLog.setId(UUID.randomUUID());
        auditLog.setAdminUserId(adminUserId);
        auditLog.setTargetTenantId(targetTenantId);
        auditLog.setAccessedAt(OffsetDateTime.now());
        auditLogRepository.save(auditLog);
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        String title;
        if (status == HttpStatus.FORBIDDEN) {
            title = "Acesso negado";
        } else if (status == HttpStatus.NOT_FOUND) {
            title = "Não encontrado";
        } else {
            title = "Requisição inválida";
        }
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
