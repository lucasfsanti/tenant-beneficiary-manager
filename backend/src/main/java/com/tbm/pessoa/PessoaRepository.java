package com.tbm.pessoa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

    Optional<Pessoa> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

    Page<Pessoa> findByNomeContainingIgnoreCase(String nome, Pageable pageable);

    /**
     * Existence-only check against beneficiario, used by the Pessoa-deletion pre-check
     * (FR-005). Deliberately a native query rather than a JPA relationship so PessoaRepository
     * carries no compile-time dependency on the Beneficiario entity, and so it never learns
     * which tenant(s) hold the reference (data-model.md).
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM beneficiario WHERE pessoa_id = :pessoaId)", nativeQuery = true)
    boolean existsBeneficiarioReferencing(@Param("pessoaId") UUID pessoaId);
}
