package com.tbm.user;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.tbm.tenant.Tenant;

@Entity
@Table(name = "user_tenant_membership")
public class UserTenantMembership {

    @EmbeddedId
    private UserTenantMembershipId id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @MapsId("userId")
    @jakarta.persistence.JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @MapsId("tenantId")
    @jakarta.persistence.JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "is_tenant_admin", nullable = false)
    private boolean isTenantAdmin;

    public UserTenantMembership() {
    }

    public UserTenantMembership(AppUser user, Tenant tenant) {
        this.user = user;
        this.tenant = tenant;
        this.id = new UserTenantMembershipId(user.getId(), tenant.getId());
    }

    public UserTenantMembershipId getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public boolean isTenantAdmin() {
        return isTenantAdmin;
    }

    public void setTenantAdmin(boolean tenantAdmin) {
        this.isTenantAdmin = tenantAdmin;
    }
}
