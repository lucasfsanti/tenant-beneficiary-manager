package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tbm.common.exception.UnauthorizedException;
import com.tbm.security.JwtService;
import com.tbm.user.AppUser;
import com.tbm.user.AppUserRepository;
import com.tbm.user.AuthService;
import com.tbm.user.UserTenantMembershipRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Covers AuthService's defensive paths that a real HTTP flow cannot trigger: there is no way to
 * hold a signed JWT for a username/userId the platform no longer recognizes, since the only way
 * to obtain a token is a successful login and there is no account-deletion endpoint. */
class AuthServiceTest {

    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final UserTenantMembershipRepository membershipRepository =
            mock(UserTenantMembershipRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService authService =
            new AuthService(userRepository, membershipRepository, passwordEncoder, jwtService);

    @Test
    void loginRejectsAnUnknownUsername() {
        when(userRepository.findByUsername("desconhecido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("desconhecido", "qualquer"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsAWrongPassword() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setUsername("alguem");
        user.setPasswordHash("hash-armazenado");
        when(userRepository.findByUsername("alguem")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alguem", "senha-errada"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getProfileRejectsAUserIdThatNoLongerExists() {
        UUID vanishedUserId = UUID.randomUUID();
        when(userRepository.findById(vanishedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getProfile(vanishedUserId))
                .isInstanceOf(UnauthorizedException.class);
    }
}
