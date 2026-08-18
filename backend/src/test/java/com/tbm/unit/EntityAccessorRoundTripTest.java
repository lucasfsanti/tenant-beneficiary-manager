package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.tenant.Tenant;
import com.tbm.user.AppUser;
import com.tbm.user.UserTenantMembership;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Plain JPA entity accessors used only by JPA/seed setup, not by application service code —
 * still worth a direct round-trip check since nothing else in the suite exercises them. */
class EntityAccessorRoundTripTest {

    @Test
    void appUserAccessorsRoundTrip() {
        AppUser user = new AppUser();
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();

        user.setId(id);
        user.setUsername("alguem");
        user.setPasswordHash("hash");
        user.setCreatedAt(createdAt);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("alguem");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void tenantCreatedAtRoundTrips() {
        Tenant tenant = new Tenant();
        OffsetDateTime createdAt = OffsetDateTime.now();
        tenant.setCreatedAt(createdAt);
        assertThat(tenant.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void userTenantMembershipIdIsDerivedFromItsUserAndTenant() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        UserTenantMembership membership = new UserTenantMembership(user, tenant);

        assertThat(membership.getId().getUserId()).isEqualTo(user.getId());
        assertThat(membership.getId().getTenantId()).isEqualTo(tenant.getId());
    }
}
