package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.user.UserTenantMembershipId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UserTenantMembershipId is a JPA @Embeddable composite key — its equals/hashCode contract
 * determines entity identity, so it is worth verifying directly rather than only transitively
 * through persistence behavior. */
class UserTenantMembershipIdTest {

    @Test
    void equalsIsReflexive() {
        UserTenantMembershipId id = new UserTenantMembershipId(UUID.randomUUID(), UUID.randomUUID());
        assertThat(id).isEqualTo(id);
    }

    @Test
    void equalsIsFalseForADifferentType() {
        UserTenantMembershipId id = new UserTenantMembershipId(UUID.randomUUID(), UUID.randomUUID());
        assertThat(id).isNotEqualTo("not an id");
    }

    @Test
    void equalsAndHashCodeAgreeForEqualFields() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserTenantMembershipId a = new UserTenantMembershipId(userId, tenantId);
        UserTenantMembershipId b = new UserTenantMembershipId(userId, tenantId);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equalsIsFalseForDifferentFields() {
        UserTenantMembershipId a = new UserTenantMembershipId(UUID.randomUUID(), UUID.randomUUID());
        UserTenantMembershipId b = new UserTenantMembershipId(UUID.randomUUID(), UUID.randomUUID());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void equalsIsFalseWhenOnlyTheTenantIdDiffers() {
        UUID sharedUserId = UUID.randomUUID();
        UserTenantMembershipId a = new UserTenantMembershipId(sharedUserId, UUID.randomUUID());
        UserTenantMembershipId b = new UserTenantMembershipId(sharedUserId, UUID.randomUUID());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void gettersAndSettersRoundTrip() {
        UserTenantMembershipId id = new UserTenantMembershipId();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        id.setUserId(userId);
        id.setTenantId(tenantId);
        assertThat(id.getUserId()).isEqualTo(userId);
        assertThat(id.getTenantId()).isEqualTo(tenantId);
    }
}
