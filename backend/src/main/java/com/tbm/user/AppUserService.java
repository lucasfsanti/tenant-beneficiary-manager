package com.tbm.user;

import com.tbm.common.exception.BusinessRuleException;
import com.tbm.common.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    public void grantSystemAdmin(UUID targetUserId) {
        AppUser target = findOrThrow(targetUserId);
        if (!target.isSystemAdmin()) {
            target.setSystemAdmin(true);
            appUserRepository.save(target);
        }
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    public void revokeSystemAdmin(UUID targetUserId) {
        if (!appUserRepository.existsById(targetUserId)) {
            throw new NotFoundException("Usuário não encontrado.");
        }

        // Locks the whole current admin row set, not just the target — see research.md §9 and
        // AppUserRepository.findAllSystemAdminsForUpdate()'s javadoc for why that's required.
        List<AppUser> currentAdmins = appUserRepository.findAllSystemAdminsForUpdate();
        AppUser target =
                currentAdmins.stream().filter(u -> u.getId().equals(targetUserId)).findFirst().orElse(null);
        if (target == null) {
            return; // already not a System Admin — idempotent no-op (FR-014)
        }
        if (currentAdmins.size() <= 1) {
            throw new BusinessRuleException(
                    "A plataforma deve manter ao menos um System Admin; esta ação removeria o último.");
        }
        target.setSystemAdmin(false);
        appUserRepository.save(target);
    }

    private AppUser findOrThrow(UUID userId) {
        return appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }
}
