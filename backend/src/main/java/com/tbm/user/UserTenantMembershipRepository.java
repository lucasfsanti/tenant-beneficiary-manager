package com.tbm.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTenantMembershipRepository
        extends JpaRepository<UserTenantMembership, UserTenantMembershipId> {

    boolean existsByUser_IdAndTenant_Id(UUID userId, UUID tenantId);

    boolean existsByUser_IdAndTenant_IdAndIsTenantAdminTrue(UUID userId, UUID tenantId);

    boolean existsByTenant_Id(UUID tenantId);

    Optional<UserTenantMembership> findByUser_IdAndTenant_Id(UUID userId, UUID tenantId);

    /**
     * Eagerly fetches the associated Tenant so callers can read it outside the originating
     * transaction (e.g. after the repository call returns to a non-transactional caller)
     * without hitting a LazyInitializationException.
     */
    @Query("SELECT m FROM UserTenantMembership m JOIN FETCH m.tenant WHERE m.user.id = :userId")
    List<UserTenantMembership> findByUser_IdFetchTenant(@Param("userId") UUID userId);

    /** Eagerly fetches the associated User, for listing a Tenant's members with their usernames. */
    @Query("SELECT m FROM UserTenantMembership m JOIN FETCH m.user WHERE m.tenant.id = :tenantId")
    List<UserTenantMembership> findByTenant_IdFetchUser(@Param("tenantId") UUID tenantId);
}
