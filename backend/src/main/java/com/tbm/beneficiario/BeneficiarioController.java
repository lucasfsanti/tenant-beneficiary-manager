package com.tbm.beneficiario;

import com.tbm.beneficiario.dto.BeneficiarioInput;
import com.tbm.beneficiario.dto.BeneficiarioResponse;
import com.tbm.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beneficiarios")
@Tag(name = "Beneficiários", description = "Registro de Beneficiários, escopado pelo tenant ativo")
@SecurityRequirement(name = "bearerAuth")
public class BeneficiarioController {

    private final BeneficiarioService beneficiarioService;

    public BeneficiarioController(BeneficiarioService beneficiarioService) {
        this.beneficiarioService = beneficiarioService;
    }

    @GetMapping
    public PageResponse<BeneficiarioResponse> list(
            @Parameter(
                            name = "X-Tenant-Id",
                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                            required = true,
                            description = "Tenant ativo; deve ser uma das associações do usuário")
                    @RequestHeader(name = "X-Tenant-Id", required = false)
                    String tenantIdHeader,
            @RequestParam(required = false) String pessoaNome,
            @RequestParam(required = false) BeneficiarioStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Sort is fixed (ORDER BY p.nome ASC) inside BeneficiarioRepository#search itself, since
        // Pessoa is a secondary FROM alias that Pageable-driven Sort cannot address.
        Pageable pageable = PageRequest.of(page, size);
        return beneficiarioService.list(pessoaNome, status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiarioResponse create(
            @Parameter(
                            name = "X-Tenant-Id",
                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                            required = true)
                    @RequestHeader(name = "X-Tenant-Id", required = false)
                    String tenantIdHeader,
            @Valid @RequestBody BeneficiarioInput input) {
        return beneficiarioService.create(input);
    }

    @GetMapping("/{beneficiarioId}")
    public BeneficiarioResponse get(
            @Parameter(
                            name = "X-Tenant-Id",
                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                            required = true)
                    @RequestHeader(name = "X-Tenant-Id", required = false)
                    String tenantIdHeader,
            @PathVariable UUID beneficiarioId) {
        return beneficiarioService.get(beneficiarioId);
    }

    @PutMapping("/{beneficiarioId}")
    public BeneficiarioResponse update(
            @Parameter(
                            name = "X-Tenant-Id",
                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                            required = true)
                    @RequestHeader(name = "X-Tenant-Id", required = false)
                    String tenantIdHeader,
            @PathVariable UUID beneficiarioId,
            @Valid @RequestBody BeneficiarioInput input) {
        return beneficiarioService.update(beneficiarioId, input);
    }

    @DeleteMapping("/{beneficiarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(
                            name = "X-Tenant-Id",
                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                            required = true)
                    @RequestHeader(name = "X-Tenant-Id", required = false)
                    String tenantIdHeader,
            @PathVariable UUID beneficiarioId) {
        beneficiarioService.delete(beneficiarioId);
    }
}
