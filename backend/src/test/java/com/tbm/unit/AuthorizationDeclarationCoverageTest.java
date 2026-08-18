package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.tenant.MembershipService;
import com.tbm.tenant.TenantService;
import com.tbm.tenant.dto.TenantInput;
import com.tbm.user.AppUserService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Verifies spec FR-008/SC-002: the required standing for every admin-protected operation is
 * mechanically checkable from its {@code @PreAuthorize} declaration, not just documented in
 * data-model.md's Protected Operations table. If this test and that table ever diverge, one of
 * them is wrong — see also T018, which cross-checks the table against the code.
 */
class AuthorizationDeclarationCoverageTest {

    private static final String SYSTEM_ADMIN_ONLY = "hasRole('SYSTEM_ADMIN')";
    private static final String SYSTEM_ADMIN_OR_TENANT_ADMIN =
            "hasRole('SYSTEM_ADMIN') or @tenantAuthorization.isTenantAdmin(#tenantId)";

    private record Expectation(Class<?> type, String methodName, Class<?>[] paramTypes, String expression) {
    }

    private static final List<Expectation> EXPECTATIONS =
            List.of(
                    // TenantService — data-model.md rows 1-5
                    new Expectation(TenantService.class, "list", new Class<?>[] {}, SYSTEM_ADMIN_ONLY),
                    new Expectation(
                            TenantService.class,
                            "create",
                            new Class<?>[] {TenantInput.class},
                            SYSTEM_ADMIN_ONLY),
                    new Expectation(
                            TenantService.class,
                            "get",
                            new Class<?>[] {UUID.class},
                            SYSTEM_ADMIN_OR_TENANT_ADMIN),
                    new Expectation(
                            TenantService.class,
                            "update",
                            new Class<?>[] {UUID.class, TenantInput.class},
                            SYSTEM_ADMIN_OR_TENANT_ADMIN),
                    new Expectation(
                            TenantService.class, "delete", new Class<?>[] {UUID.class}, SYSTEM_ADMIN_ONLY),
                    // MembershipService — data-model.md rows 6-10
                    new Expectation(
                            MembershipService.class,
                            "listMembers",
                            new Class<?>[] {UUID.class},
                            SYSTEM_ADMIN_OR_TENANT_ADMIN),
                    new Expectation(
                            MembershipService.class,
                            "addMember",
                            new Class<?>[] {UUID.class, UUID.class},
                            SYSTEM_ADMIN_OR_TENANT_ADMIN),
                    new Expectation(
                            MembershipService.class,
                            "removeMember",
                            new Class<?>[] {UUID.class, UUID.class},
                            SYSTEM_ADMIN_OR_TENANT_ADMIN),
                    new Expectation(
                            MembershipService.class,
                            "grantTenantAdmin",
                            new Class<?>[] {UUID.class, UUID.class},
                            SYSTEM_ADMIN_OR_TENANT_ADMIN),
                    new Expectation(
                            MembershipService.class,
                            "revokeTenantAdmin",
                            new Class<?>[] {UUID.class, UUID.class},
                            SYSTEM_ADMIN_OR_TENANT_ADMIN),
                    // AppUserService — data-model.md rows 11-12
                    new Expectation(
                            AppUserService.class,
                            "grantSystemAdmin",
                            new Class<?>[] {UUID.class},
                            SYSTEM_ADMIN_ONLY),
                    new Expectation(
                            AppUserService.class,
                            "revokeSystemAdmin",
                            new Class<?>[] {UUID.class},
                            SYSTEM_ADMIN_ONLY));

    @Test
    void everyProtectedOperationDeclaresItsExactExpectedPreAuthorizeExpression() throws NoSuchMethodException {
        for (Expectation expectation : EXPECTATIONS) {
            Method method = expectation.type().getDeclaredMethod(expectation.methodName(), expectation.paramTypes());
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

            assertThat(annotation)
                    .as("%s.%s must be annotated with @PreAuthorize", expectation.type().getSimpleName(),
                            expectation.methodName())
                    .isNotNull();
            assertThat(annotation.value())
                    .as("%s.%s's @PreAuthorize expression", expectation.type().getSimpleName(), expectation.methodName())
                    .isEqualTo(expectation.expression());
        }
    }

    @Test
    void noOtherPublicMethodOnTheseThreeClassesIsLeftUnprotected() {
        // spec FR-010: converting the admin-gated operations in a class must not leave a sibling
        // operation untouched — verified here by confirming these three classes have exactly the
        // public methods listed in EXPECTATIONS above, no more, no fewer.
        assertPublicMethodCount(TenantService.class, 5);
        assertPublicMethodCount(MembershipService.class, 5);
        assertPublicMethodCount(AppUserService.class, 2);
    }

    private void assertPublicMethodCount(Class<?> type, int expectedCount) {
        long actualCount =
                java.util.Arrays.stream(type.getDeclaredMethods())
                        .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                        .filter(m -> !m.isSynthetic())
                        .count();
        assertThat(actualCount).as("public method count on %s", type.getSimpleName()).isEqualTo(expectedCount);
    }
}
