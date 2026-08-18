package com.tbm.user.dto;

import java.util.List;
import java.util.UUID;

public record UserProfile(UUID id, String username, List<TenantSummary> tenants) {
}
