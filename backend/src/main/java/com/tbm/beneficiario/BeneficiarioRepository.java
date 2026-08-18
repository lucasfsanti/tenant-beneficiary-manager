package com.tbm.beneficiario;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query here is explicitly tenant-scoped by an incoming {@code tenantId} parameter — the
 * caller (BeneficiarioService) is the single place that reads the resolved
 * {@link com.tbm.security.TenantContext}, so no method here can accidentally run unscoped
 * (Constitution Principle I).
 */
public interface BeneficiarioRepository extends JpaRepository<Beneficiario, UUID> {

    Optional<Beneficiario> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndMatricula(UUID tenantId, String matricula);

    boolean existsByTenantIdAndMatriculaAndIdNot(UUID tenantId, String matricula, UUID id);

    /**
     * Pessoa is joined by id (no JPA relationship is declared between the two entities — see
     * data-model.md) so that {@code pessoaNome} and {@code status} can both be filtered.
     * {@code pessoaNome} must be {@code ""} (never {@code null}) to skip that predicate — a null
     * String bind makes PgJDBC guess the parameter's SQL type as bytea inside the CONCAT/LOWER
     * expression, since there is no column type to infer it from at that point in the query.
     * {@code status} may be {@code null} to skip its predicate; Hibernate resolves its SQL type
     * from the {@code b.status} comparison itself, so no such ambiguity applies there.
     */
    @Query(
            "SELECT b FROM Beneficiario b, com.tbm.pessoa.Pessoa p "
                    + "WHERE b.pessoaId = p.id AND b.tenantId = :tenantId "
                    + "AND (:pessoaNome = '' OR LOWER(p.nome) LIKE LOWER(CONCAT('%', :pessoaNome, '%'))) "
                    + "AND (:status IS NULL OR b.status = :status) "
                    + "ORDER BY p.nome ASC")
    Page<Beneficiario> search(
            @Param("tenantId") UUID tenantId,
            @Param("pessoaNome") String pessoaNome,
            @Param("status") BeneficiarioStatus status,
            Pageable pageable);
}
