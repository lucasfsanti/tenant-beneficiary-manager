package com.tbm.tenant;

import com.tbm.tenant.dto.AddMemberRequest;
import com.tbm.tenant.dto.MemberResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/tenants/{tenantId}/members")
@Tag(name = "Membros do Tenant", description = "Gerenciamento de membros e status de Tenant Admin")
@SecurityRequirement(name = "bearerAuth")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    public List<MemberResponse> list(@PathVariable UUID tenantId) {
        return membershipService.listMembers(tenantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse add(
            @PathVariable UUID tenantId, @Valid @RequestBody AddMemberRequest request) {
        return membershipService.addMember(tenantId, request.userId());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        membershipService.removeMember(tenantId, userId);
    }

    @PutMapping("/{userId}/tenant-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantTenantAdmin(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        membershipService.grantTenantAdmin(tenantId, userId);
    }

    @DeleteMapping("/{userId}/tenant-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeTenantAdmin(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        membershipService.revokeTenantAdmin(tenantId, userId);
    }
}
