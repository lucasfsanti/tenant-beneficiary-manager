package com.tbm.user;

import com.tbm.common.exception.UnauthorizedException;
import com.tbm.security.JwtService;
import com.tbm.user.dto.LoginResponse;
import com.tbm.user.dto.TenantSummary;
import com.tbm.user.dto.UserProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            UserTenantMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        AppUser user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(
                                () -> new UnauthorizedException("Usuário ou senha inválidos."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Usuário ou senha inválidos.");
        }

        UserProfile profile = buildProfile(user);
        List<UUID> tenantIds = profile.tenants().stream().map(TenantSummary::id).toList();
        String token = jwtService.issueToken(user.getId(), user.getUsername(), tenantIds);
        return new LoginResponse(token, profile);
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        AppUser user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado."));
        return buildProfile(user);
    }

    private UserProfile buildProfile(AppUser user) {
        List<TenantSummary> tenants =
                membershipRepository.findByUser_IdFetchTenant(user.getId()).stream()
                        .map(
                                m ->
                                        new TenantSummary(
                                                m.getTenant().getId(),
                                                m.getTenant().getNome(),
                                                m.isTenantAdmin()))
                        .toList();
        return new UserProfile(user.getId(), user.getUsername(), user.isSystemAdmin(), tenants);
    }
}
