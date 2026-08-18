package com.tbm.pessoa;

import com.tbm.common.dto.PageResponse;
import com.tbm.pessoa.dto.PessoaInput;
import com.tbm.pessoa.dto.PessoaResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pessoas")
@Tag(name = "Pessoas", description = "Registro global de Pessoas, independente de tenant")
@SecurityRequirement(name = "bearerAuth")
public class PessoaController {

    private final PessoaService pessoaService;

    public PessoaController(PessoaService pessoaService) {
        this.pessoaService = pessoaService;
    }

    @GetMapping
    public PageResponse<PessoaResponse> list(
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nome").ascending());
        return pessoaService.list(nome, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PessoaResponse create(@Valid @RequestBody PessoaInput input) {
        return pessoaService.create(input);
    }

    @GetMapping("/{pessoaId}")
    public PessoaResponse get(@PathVariable UUID pessoaId) {
        return pessoaService.get(pessoaId);
    }

    @PutMapping("/{pessoaId}")
    public PessoaResponse update(@PathVariable UUID pessoaId, @Valid @RequestBody PessoaInput input) {
        return pessoaService.update(pessoaId, input);
    }

    @DeleteMapping("/{pessoaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID pessoaId) {
        pessoaService.delete(pessoaId);
    }
}
