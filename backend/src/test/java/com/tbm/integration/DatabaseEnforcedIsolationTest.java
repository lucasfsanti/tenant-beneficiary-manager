package com.tbm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves tenant filtering is enforced by {@code vw_beneficiario} itself — via raw JDBC, entirely
 * bypassing the application layer (repositories, services, controllers) — rather than by any
 * Java code that could have a bug or be forgotten on a new code path (User Story 1, spec
 * 007-tenant-transparent-views).
 */
class DatabaseEnforcedIsolationTest extends AbstractIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    void viewReturnsNoRowsWithoutATenantContext() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            assertThat(count(connection, "SELECT count(*) FROM vw_beneficiario")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM beneficiario")).isGreaterThan(0);

            connection.rollback();
        }
    }

    @Test
    void viewMatchesTheBaseTableFilteredByTenantOnceTheSessionContextIsSet() throws Exception {
        long baseTableAlfaCount;
        try (Connection lookup = dataSource.getConnection()) {
            baseTableAlfaCount =
                    count(
                            lookup,
                            "SELECT count(*) FROM beneficiario WHERE tenant_id = '"
                                    + TENANT_ALFA_ID
                                    + "'");
        }
        assertThat(baseTableAlfaCount).isGreaterThan(0);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenantContext(connection, TENANT_ALFA_ID);

            assertThat(count(connection, "SELECT count(*) FROM vw_beneficiario"))
                    .isEqualTo(baseTableAlfaCount);

            connection.commit();
        }
    }

    @Test
    void sessionContextDoesNotLeakAcrossTransactionsOnAReusedConnection() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setTenantContext(connection, TENANT_ALFA_ID);
            assertThat(count(connection, "SELECT count(*) FROM vw_beneficiario")).isGreaterThan(0);
            connection.commit();

            // is_local=true resets app.tenant_id at COMMIT — a fresh transaction on the very same
            // connection must see no tenant context, proving no leak into whatever transaction
            // reuses a pooled connection next.
            assertThat(count(connection, "SELECT count(*) FROM vw_beneficiario")).isZero();
            connection.rollback();
        }
    }

    /**
     * FR-010/SC-003: the migration (006-tenant-view-and-audit-log.sql) only adds a column
     * default and a view — it never renames or copies the base table — so seeded rows from
     * 002-seed-demo-data.sql must still be present with their original values, unaffected.
     */
    @Test
    void migrationPreservedKnownSeededRows() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "SELECT pessoa_id, tenant_id, matricula, tipo, status FROM"
                                    + " beneficiario WHERE id = ?")) {
                statement.setObject(1, java.util.UUID.fromString(
                        "66666666-6666-6666-6666-666666666661"));
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("pessoa_id"))
                            .isEqualTo("55555555-5555-5555-5555-555555555551");
                    assertThat(resultSet.getString("tenant_id")).isEqualTo(TENANT_ALFA_ID);
                    assertThat(resultSet.getString("matricula"))
                            .isEqualTo("Beneficiário 1 - Tenant 1");
                    assertThat(resultSet.getString("tipo")).isEqualTo("TITULAR");
                    assertThat(resultSet.getString("status")).isEqualTo("ATIVO");
                }
            }

            connection.rollback();
        }
    }

    private void setTenantContext(Connection connection, String tenantId) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenantId);
            statement.execute();
        }
    }

    private long count(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
