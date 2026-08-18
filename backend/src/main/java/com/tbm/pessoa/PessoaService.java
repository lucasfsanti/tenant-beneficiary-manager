package com.tbm.pessoa;

import com.tbm.common.dto.PageResponse;
import com.tbm.common.exception.ConflictException;
import com.tbm.common.exception.NotFoundException;
import com.tbm.pessoa.dto.PessoaInput;
import com.tbm.pessoa.dto.PessoaResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PessoaService {

    private final PessoaRepository pessoaRepository;

    public PessoaService(PessoaRepository pessoaRepository) {
        this.pessoaRepository = pessoaRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PessoaResponse> list(String nome, Pageable pageable) {
        Page<Pessoa> page =
                (nome == null || nome.isBlank())
                        ? pessoaRepository.findAll(pageable)
                        : pessoaRepository.findByNomeContainingIgnoreCase(nome, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PessoaResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public PessoaResponse create(PessoaInput input) {
        if (pessoaRepository.existsByCpf(input.cpf())) {
            throw new ConflictException("CPF já cadastrado.");
        }
        Pessoa pessoa = new Pessoa();
        pessoa.setId(UUID.randomUUID());
        applyInput(pessoa, input);
        OffsetDateTime now = OffsetDateTime.now();
        pessoa.setCreatedAt(now);
        pessoa.setUpdatedAt(now);
        return toResponse(pessoaRepository.save(pessoa));
    }

    @Transactional
    public PessoaResponse update(UUID id, PessoaInput input) {
        Pessoa pessoa = findOrThrow(id);
        if (!pessoa.getCpf().equals(input.cpf()) && pessoaRepository.existsByCpf(input.cpf())) {
            throw new ConflictException("CPF já cadastrado.");
        }
        applyInput(pessoa, input);
        pessoa.setUpdatedAt(OffsetDateTime.now());
        return toResponse(pessoaRepository.save(pessoa));
    }

    @Transactional
    public void delete(UUID id) {
        Pessoa pessoa = findOrThrow(id);
        if (pessoaRepository.existsBeneficiarioReferencing(pessoa.getId())) {
            throw new ConflictException(
                    "Esta Pessoa ainda está vinculada a um ou mais registros de Beneficiário.");
        }
        pessoaRepository.delete(pessoa);
    }

    private Pessoa findOrThrow(UUID id) {
        return pessoaRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Pessoa não encontrada."));
    }

    private void applyInput(Pessoa pessoa, PessoaInput input) {
        pessoa.setNome(input.nome());
        pessoa.setCpf(input.cpf());
        pessoa.setDataNascimento(input.dataNascimento());
        pessoa.setEmail(input.email());
    }

    private PessoaResponse toResponse(Pessoa pessoa) {
        return new PessoaResponse(
                pessoa.getId(),
                pessoa.getNome(),
                pessoa.getCpf(),
                pessoa.getDataNascimento(),
                pessoa.getEmail(),
                pessoa.getCreatedAt(),
                pessoa.getUpdatedAt());
    }
}
