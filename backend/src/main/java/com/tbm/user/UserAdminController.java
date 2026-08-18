package com.tbm.user;

import com.tbm.security.JwtService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuários", description = "Concessão/revogação de status de System Admin")
@SecurityRequirement(name = "bearerAuth")
public class UserAdminController {

    private final AppUserService appUserService;

    public UserAdminController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @PutMapping("/{userId}/system-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(
            @PathVariable UUID userId, @AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        appUserService.grantSystemAdmin(userId, principal.userId());
    }

    @DeleteMapping("/{userId}/system-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable UUID userId, @AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        appUserService.revokeSystemAdmin(userId, principal.userId());
    }
}
