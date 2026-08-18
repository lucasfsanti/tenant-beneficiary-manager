package com.tbm.user;

import com.tbm.security.JwtService;
import com.tbm.user.dto.LoginRequest;
import com.tbm.user.dto.LoginResponse;
import com.tbm.user.dto.UserProfile;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Autenticação", description = "Login simplificado e perfil do usuário autenticado")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserProfile me(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return authService.getProfile(principal.userId());
    }
}
