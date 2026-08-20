package com.tbm.security;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies the resolved active tenant to the current database transaction, so that
 * {@code vw_beneficiario} (and the {@code beneficiario} base table's {@code tenant_id} default)
 * can filter/stamp rows by it — the database-level half of Constitution Principle I, alongside
 * {@link TenantContext} (research.md §1, §3 of spec 007-tenant-transparent-views).
 *
 * <p>{@code is_local = true} makes this a {@code SET LOCAL}-equivalent, transaction-scoped
 * setting that PostgreSQL resets automatically at {@code COMMIT}/{@code ROLLBACK} — it cannot
 * leak into whatever transaction/tenant reuses a pooled connection next.
 */
@Component
public class TenantSessionContext {

    private final EntityManager entityManager;

    public TenantSessionContext(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void apply(UUID tenantId) {
        entityManager
                .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId.toString())
                .getSingleResult();
    }
}
