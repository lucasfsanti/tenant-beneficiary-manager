package com.tbm.user;

import com.tbm.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuários", description = "Busca de usuários existentes por username exato (seletor de membros)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AppUserRepository appUserRepository;

    public UserController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public List<UserSummary> search(@RequestParam String username) {
        return appUserRepository
                .findByUsername(username)
                .map(u -> new UserSummary(u.getId(), u.getUsername()))
                .map(List::of)
                .orElseGet(List::of);
    }
}
