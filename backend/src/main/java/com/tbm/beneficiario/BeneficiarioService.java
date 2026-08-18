package com.tbm.beneficiario;

import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import com.tbm.common.dto.PageResponse;
import com.tbm.common.exception.BusinessRuleException;
import com.tbm.common.exception.ConflictException;
import com.tbm.common.exception.NotFoundException;
import com.tbm.pessoa.Pessoa;
import com.tbm.pessoa.PessoaRepository;
import com.tbm.security.TenantContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiarioService {

    private final BeneficiarioRepository beneficiarioRepository;
    private final PessoaRepository pessoaRepository;

    public BeneficiarioService(
            BeneficiarioRepository beneficiarioRepository, PessoaRepository pessoaRepository) {
        this.beneficiarioRepository = beneficiarioRepository;
        this.pessoaRepository = pessoaRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<BeneficiarioResponse> list(
            String pessoaNome, BeneficiarioStatus status, Pageable pageable) {
        UUID tenantId = activeTenantId();
        String normalizedNome = (pessoaNome == null || pessoaNome.isBlank()) ? "" : pessoaNome;
        Page<Beneficiario> page =
                beneficiarioRepository.search(tenantId, normalizedNome, status, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public BeneficiarioResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public BeneficiarioResponse create(BeneficiarioInput input) {
        UUID tenantId = activeTenantId();
        Pessoa pessoa =
                pessoaRepository
                        .findById(input.pessoaId())
                        .orElseThrow(
                                () ->
                                        new BusinessRuleException(
                                                "A Pessoa informada não existe."));
        if (beneficiarioRepository.existsByTenantIdAndMatricula(tenantId, input.matricula())) {
            throw new ConflictException("Matrícula já cadastrada neste tenant.");
        }

        Beneficiario beneficiario = new Beneficiario();
        beneficiario.setId(UUID.randomUUID());
        beneficiario.setTenantId(tenantId);
        beneficiario.setPessoaId(pessoa.getId());
        applyInput(beneficiario, input);
        // FR-023: omitting dataAdesao on creation defaults it to the record's creation date.
        if (input.dataAdesao() == null) {
            beneficiario.setDataAdesao(LocalDate.now());
        }
        OffsetDateTime now = OffsetDateTime.now();
        beneficiario.setCreatedAt(now);
        beneficiario.setUpdatedAt(now);
        return toResponse(beneficiarioRepository.save(beneficiario));
    }

    @Transactional
    public BeneficiarioResponse update(UUID id, BeneficiarioInput input) {
        Beneficiario beneficiario = findOrThrow(id);
        UUID tenantId = beneficiario.getTenantId();

        if (!pessoaRepository.existsById(input.pessoaId())) {
            throw new BusinessRuleException("A Pessoa informada não existe.");
        }
        if (beneficiarioRepository.existsByTenantIdAndMatriculaAndIdNot(
                tenantId, input.matricula(), id)) {
            throw new ConflictException("Matrícula já cadastrada neste tenant.");
        }

        beneficiario.setPessoaId(input.pessoaId());
        applyInput(beneficiario, input);
        beneficiario.setUpdatedAt(OffsetDateTime.now());
        return toResponse(beneficiarioRepository.save(beneficiario));
    }

    @Transactional
    public void delete(UUID id) {
        Beneficiario beneficiario = findOrThrow(id);
        beneficiarioRepository.delete(beneficiario);
    }

    private Beneficiario findOrThrow(UUID id) {
        UUID tenantId = activeTenantId();
        return beneficiarioRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Beneficiário não encontrado."));
    }

    private UUID activeTenantId() {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new BusinessRuleException("Nenhum tenant ativo resolvido para a requisição.");
        }
        return tenantId;
    }

    private void applyInput(Beneficiario beneficiario, BeneficiarioInput input) {
        beneficiario.setMatricula(input.matricula());
        beneficiario.setTipo(input.tipo());
        beneficiario.setStatus(input.status());
        // On update, omitting dataAdesao preserves the current value rather than resetting it;
        // only creation (FR-023) defaults an omitted dataAdesao to "today".
        if (input.dataAdesao() != null) {
            beneficiario.setDataAdesao(input.dataAdesao());
        }
    }

    private BeneficiarioResponse toResponse(Beneficiario beneficiario) {
        String pessoaNome =
                pessoaRepository
                        .findById(beneficiario.getPessoaId())
                        .map(Pessoa::getNome)
                        .orElse(null);
        return new BeneficiarioResponse(
                beneficiario.getId(),
                beneficiario.getTenantId(),
                beneficiario.getPessoaId(),
                pessoaNome,
                beneficiario.getMatricula(),
                beneficiario.getTipo(),
                beneficiario.getStatus(),
                beneficiario.getDataAdesao(),
                beneficiario.getCreatedAt(),
                beneficiario.getUpdatedAt());
    }
}
