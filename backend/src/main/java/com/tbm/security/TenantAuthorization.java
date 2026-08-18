package com.tbm.security;

import com.tbm.user.UserTenantMembershipRepository;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Referenced from {@code @PreAuthorize} SpEL expressions to check Tenant Admin standing for a
 * specific tenant — a per-resource permission that, unlike System Admin, cannot be represented as
 * a single static {@code GrantedAuthority} (research.md §2).
 */
@Component
public class TenantAuthorization {

    private final UserTenantMembershipRepository membershipRepository;

    public TenantAuthorization(UserTenantMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public boolean isTenantAdmin(UUID tenantId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof JwtService.JwtPrincipal principal)) {
            return false;
        }
        return membershipRepository.existsByUser_IdAndTenant_IdAndIsTenantAdminTrue(
                principal.userId(), tenantId);
    }
}
