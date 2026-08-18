package com.tbm.security;

import java.util.UUID;

/**
 * Holds the active tenant id for the current request thread. Populated exactly once, by
 * {@link TenantContextFilter}, and read by every Beneficiario repository call — the single
 * centralized enforcement point for Constitution Principle I.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> ACTIVE_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        ACTIVE_TENANT_ID.set(tenantId);
    }

    public static UUID get() {
        return ACTIVE_TENANT_ID.get();
    }

    public static void clear() {
        ACTIVE_TENANT_ID.remove();
    }
}
