package com.tbm.user;

import com.tbm.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(
        name = "Usuários",
        description =
                "Busca de usuários existentes por trecho do username (case-insensitive, mínimo de"
                        + " 2 caracteres, limitado a 20 resultados) — seletor de membros")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_RESULTS = 20;

    private final AppUserRepository appUserRepository;

    public UserController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping
    public List<UserSummary> search(@RequestParam String username) {
        String trimmed = username.trim();
        if (trimmed.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        return appUserRepository.searchByUsername(trimmed, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername()))
                .toList();
    }
}
