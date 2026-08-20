package com.tbm.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link TenantSessionContext} in isolation: the exact {@code set_config} call it issues,
 * and that a failure to establish the tenant context is never swallowed (FR-003 fail-closed,
 * research.md §4 of spec 007-tenant-transparent-views).
 */
class TenantSessionContextTest {

    @Test
    void appliesTheTenantIdViaATransactionScopedSetConfigCall() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)"))
                .thenReturn(query);
        when(query.setParameter(eq("tenantId"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(query);

        UUID tenantId = UUID.randomUUID();
        new TenantSessionContext(entityManager).apply(tenantId);

        verify(entityManager)
                .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)");
        verify(query).setParameter("tenantId", tenantId.toString());
        verify(query).getSingleResult();
    }

    @Test
    void propagatesAFailureToEstablishTheContextRatherThanSwallowingIt() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(query);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.getSingleResult()).thenThrow(new RuntimeException("db unavailable"));

        TenantSessionContext tenantSessionContext = new TenantSessionContext(entityManager);
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> tenantSessionContext.apply(tenantId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db unavailable");
    }
}
