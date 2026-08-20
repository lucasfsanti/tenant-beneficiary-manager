package com.tbm.security;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAccessAuditLogRepository extends JpaRepository<TenantAccessAuditLog, UUID> {

    boolean existsByTargetTenantId(UUID targetTenantId);
}
