package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tbm.common.exception.ConflictException;
import com.tbm.common.exception.UnauthorizedException;
import com.tbm.security.JwtService;
import com.tbm.user.AppUser;
import com.tbm.user.AppUserRepository;
import com.tbm.user.AuthService;
import com.tbm.user.UserTenantMembershipRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    /** spec 006-user-self-registration FR-003: the very first account is granted System Admin,
     * decided from AppUserRepository's advisory-lock-guarded check, never from client input. */
    @Test
    void registerGrantsSystemAdminWhenNoAccountExistsYet() {
        when(userRepository.findByUsername("primeiro")).thenReturn(Optional.empty());
        when(userRepository.anyAccountExists()).thenReturn(false);
        when(passwordEncoder.encode("senha")).thenReturn("hash-gerado");

        authService.register("primeiro", "senha");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isSystemAdmin()).isTrue();
        assertThat(captor.getValue().getUsername()).isEqualTo("primeiro");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash-gerado");
    }

    /** spec FR-005: every account after the first is always Normal. */
    @Test
    void registerGrantsNormalWhenAnAccountAlreadyExists() {
        when(userRepository.findByUsername("segundo")).thenReturn(Optional.empty());
        when(userRepository.anyAccountExists()).thenReturn(true);
        when(passwordEncoder.encode("senha")).thenReturn("hash-gerado");

        authService.register("segundo", "senha");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isSystemAdmin()).isFalse();
    }

    /** spec FR-002: a duplicate username is rejected before the emptiness check is even
     * performed — a request that's going to fail anyway must not contend for the advisory lock. */
    @Test
    void registerRejectsADuplicateUsernameWithoutCheckingEmptinessOrSaving() {
        AppUser existing = new AppUser();
        existing.setUsername("ja-existe");
        when(userRepository.findByUsername("ja-existe")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register("ja-existe", "senha"))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).acquireFirstAccountDecisionLock();
        verify(userRepository, never()).anyAccountExists();
        verify(userRepository, never()).save(any());
    }
}
