package com.tbm.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per System Admin access to a tenant they are not a member of, written by
 * {@link TenantContextFilter} at the moment it grants the cross-tenant bypass (FR-013, spec
 * 007-tenant-transparent-views). Insert-only — never updated or deleted by the application.
 */
@Entity
@Table(name = "tenant_access_audit_log")
public class TenantAccessAuditLog {

    @Id
    private UUID id;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "target_tenant_id", nullable = false)
    private UUID targetTenantId;

    @Column(name = "accessed_at", nullable = false)
    private OffsetDateTime accessedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(UUID adminUserId) {
        this.adminUserId = adminUserId;
    }

    public UUID getTargetTenantId() {
        return targetTenantId;
    }

    public void setTargetTenantId(UUID targetTenantId) {
        this.targetTenantId = targetTenantId;
    }

    public OffsetDateTime getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(OffsetDateTime accessedAt) {
        this.accessedAt = accessedAt;
    }
}
