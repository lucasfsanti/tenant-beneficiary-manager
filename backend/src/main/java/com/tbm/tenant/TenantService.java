package com.tbm.tenant;

import com.tbm.beneficiario.BeneficiarioRepository;
import com.tbm.common.exception.BusinessRuleException;
import com.tbm.common.exception.ForbiddenException;
import com.tbm.common.exception.NotFoundException;
import com.tbm.tenant.dto.TenantInput;
import com.tbm.tenant.dto.TenantResponse;
import com.tbm.user.AppUserRepository;
import com.tbm.user.UserTenantMembershipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final BeneficiarioRepository beneficiarioRepository;

    public TenantService(
            TenantRepository tenantRepository,
            AppUserRepository appUserRepository,
            UserTenantMembershipRepository membershipRepository,
            BeneficiarioRepository beneficiarioRepository) {
        this.tenantRepository = tenantRepository;
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.beneficiarioRepository = beneficiarioRepository;
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list(UUID callerId) {
        requireSystemAdmin(callerId);
        return tenantRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID tenantId, UUID callerId) {
        requireSystemAdminOrTenantAdmin(callerId, tenantId);
        return toResponse(findOrThrow(tenantId));
    }

    @Transactional
    public TenantResponse create(TenantInput input, UUID callerId) {
        requireSystemAdmin(callerId);
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setNome(input.name());
        tenant.setCreatedAt(OffsetDateTime.now());
        return toResponse(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantResponse update(UUID tenantId, TenantInput input, UUID callerId) {
        requireSystemAdminOrTenantAdmin(callerId, tenantId);
        Tenant tenant = findOrThrow(tenantId);
        tenant.setNome(input.name());
        return toResponse(tenantRepository.save(tenant));
    }

    @Transactional
    public void delete(UUID tenantId, UUID callerId) {
        requireSystemAdmin(callerId);
        Tenant tenant = findOrThrow(tenantId);
        if (beneficiarioRepository.existsByTenantId(tenantId)
                || membershipRepository.existsByTenant_Id(tenantId)) {
            throw new BusinessRuleException(
                    "Este Tenant ainda está vinculado a registros de Beneficiário ou associações de usuário.");
        }
        tenantRepository.delete(tenant);
    }

    private void requireSystemAdmin(UUID callerId) {
        if (!isSystemAdmin(callerId)) {
            throw new ForbiddenException("Apenas um System Admin pode realizar esta ação.");
        }
    }

    private void requireSystemAdminOrTenantAdmin(UUID callerId, UUID tenantId) {
        if (isSystemAdmin(callerId)) {
            return;
        }
        if (!membershipRepository.existsByUser_IdAndTenant_IdAndIsTenantAdminTrue(callerId, tenantId)) {
            throw new ForbiddenException(
                    "Apenas um System Admin ou o Tenant Admin deste tenant pode realizar esta ação.");
        }
    }

    private boolean isSystemAdmin(UUID callerId) {
        return appUserRepository
                .findById(callerId)
                .map(com.tbm.user.AppUser::isSystemAdmin)
                .orElse(false);
    }

    private Tenant findOrThrow(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant não encontrado."));
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getNome());
    }
}
