package com.tbm.tenant;

import com.tbm.security.JwtService;
import com.tbm.tenant.dto.TenantInput;
import com.tbm.tenant.dto.TenantResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Gerenciamento de tenants (System Admin, ou Tenant Admin do próprio tenant)")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public List<TenantResponse> list(@AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return tenantService.list(principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(
            @Valid @RequestBody TenantInput input, @AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return tenantService.create(input, principal.userId());
    }

    @GetMapping("/{tenantId}")
    public TenantResponse get(
            @PathVariable UUID tenantId, @AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return tenantService.get(tenantId, principal.userId());
    }

    @PutMapping("/{tenantId}")
    public TenantResponse update(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantInput input,
            @AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        return tenantService.update(tenantId, input, principal.userId());
    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID tenantId, @AuthenticationPrincipal JwtService.JwtPrincipal principal) {
        tenantService.delete(tenantId, principal.userId());
    }
}
