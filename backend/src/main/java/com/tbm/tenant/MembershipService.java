package com.tbm.tenant;

import com.tbm.common.exception.ForbiddenException;
import com.tbm.common.exception.NotFoundException;
import com.tbm.tenant.dto.MemberResponse;
import com.tbm.user.AppUser;
import com.tbm.user.AppUserRepository;
import com.tbm.user.UserTenantMembership;
import com.tbm.user.UserTenantMembershipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final UserTenantMembershipRepository membershipRepository;

    public MembershipService(
            TenantRepository tenantRepository,
            AppUserRepository appUserRepository,
            UserTenantMembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID tenantId, UUID callerId) {
        requireStandingFor(callerId, tenantId);
        return membershipRepository.findByTenant_IdFetchUser(tenantId).stream()
                .map(
                        m ->
                                new MemberResponse(
                                        m.getUser().getId(), m.getUser().getUsername(), m.isTenantAdmin()))
                .toList();
    }

    @Transactional
    public MemberResponse addMember(UUID tenantId, UUID targetUserId, UUID callerId) {
        requireStandingFor(callerId, tenantId);
        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant não encontrado."));
        AppUser targetUser =
                appUserRepository
                        .findById(targetUserId)
                        .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

        UserTenantMembership membership =
                membershipRepository
                        .findByUser_IdAndTenant_Id(targetUserId, tenantId)
                        .orElseGet(() -> membershipRepository.save(new UserTenantMembership(targetUser, tenant)));
        return new MemberResponse(targetUser.getId(), targetUser.getUsername(), membership.isTenantAdmin());
    }

    @Transactional
    public void removeMember(UUID tenantId, UUID targetUserId, UUID callerId) {
        requireStandingFor(callerId, tenantId);
        membershipRepository
                .findByUser_IdAndTenant_Id(targetUserId, tenantId)
                .ifPresent(membershipRepository::delete);
    }

    @Transactional
    public void grantTenantAdmin(UUID tenantId, UUID targetUserId, UUID callerId) {
        requireStandingFor(callerId, tenantId);
        UserTenantMembership membership = findMembershipOrThrow(tenantId, targetUserId);
        if (!membership.isTenantAdmin()) {
            membership.setTenantAdmin(true);
            membershipRepository.save(membership);
        }
    }

    @Transactional
    public void revokeTenantAdmin(UUID tenantId, UUID targetUserId, UUID callerId) {
        requireStandingFor(callerId, tenantId);
        UserTenantMembership membership = findMembershipOrThrow(tenantId, targetUserId);
        if (membership.isTenantAdmin()) {
            membership.setTenantAdmin(false);
            membershipRepository.save(membership);
        }
    }

    private UserTenantMembership findMembershipOrThrow(UUID tenantId, UUID userId) {
        return membershipRepository
                .findByUser_IdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("Este usuário não é membro deste tenant."));
    }

    /** FR-005/FR-006/FR-008: System Admin platform-wide, or Tenant Admin of this specific tenant. */
    private void requireStandingFor(UUID callerId, UUID tenantId) {
        boolean isSystemAdmin = appUserRepository.findById(callerId).map(AppUser::isSystemAdmin).orElse(false);
        if (isSystemAdmin) {
            return;
        }
        if (!membershipRepository.existsByUser_IdAndTenant_IdAndIsTenantAdminTrue(callerId, tenantId)) {
            throw new ForbiddenException(
                    "Apenas um System Admin ou o Tenant Admin deste tenant pode realizar esta ação.");
        }
    }
}
