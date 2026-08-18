package com.tbm.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantInput(@NotBlank String name) {
}
