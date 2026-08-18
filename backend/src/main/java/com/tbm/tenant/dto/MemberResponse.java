package com.tbm.tenant.dto;

import java.util.UUID;

public record MemberResponse(UUID userId, String username, boolean isTenantAdmin) {
}
