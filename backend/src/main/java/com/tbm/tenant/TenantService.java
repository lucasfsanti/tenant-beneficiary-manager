package com.tbm.tenant;

import com.tbm.beneficiario.BeneficiarioRepository;
import com.tbm.common.exception.BusinessRuleException;
import com.tbm.common.exception.NotFoundException;
import com.tbm.tenant.dto.TenantInput;
import com.tbm.tenant.dto.TenantResponse;
import com.tbm.user.UserTenantMembershipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final BeneficiarioRepository beneficiarioRepository;

    public TenantService(
            TenantRepository tenantRepository,
            UserTenantMembershipRepository membershipRepository,
            BeneficiarioRepository beneficiarioRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.beneficiarioRepository = beneficiarioRepository;
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        return tenantRepository.findAll().stream().map(this::toResponse).toList();
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)")
    @Transactional(readOnly = true)
    public TenantResponse get(UUID tenantId) {
        return toResponse(findOrThrow(tenantId));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    public TenantResponse create(TenantInput input) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setNome(input.name());
        tenant.setCreatedAt(OffsetDateTime.now());
        return toResponse(tenantRepository.save(tenant));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)")
    @Transactional
    public TenantResponse update(UUID tenantId, TenantInput input) {
        Tenant tenant = findOrThrow(tenantId);
        tenant.setNome(input.name());
        return toResponse(tenantRepository.save(tenant));
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    public void delete(UUID tenantId) {
        Tenant tenant = findOrThrow(tenantId);
        if (beneficiarioRepository.existsByTenantId(tenantId)
                || membershipRepository.existsByTenant_Id(tenantId)) {
            throw new BusinessRuleException(
                    "Este Tenant ainda está vinculado a registros de Beneficiário ou associações de usuário.");
        }
        tenantRepository.delete(tenant);
    }

    private Tenant findOrThrow(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant não encontrado."));
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getNome());
    }
}
