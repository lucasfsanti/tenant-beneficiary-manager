package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.tbm.beneficiario.BeneficiarioRepository;
import com.tbm.beneficiario.BeneficiarioService;
import com.tbm.common.exception.BusinessRuleException;
import com.tbm.pessoa.PessoaRepository;
import com.tbm.security.TenantContext;
import com.tbm.security.TenantSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/** Covers BeneficiarioService's defensive "no active tenant resolved" branch — unreachable via a
 * real HTTP request, since TenantContextFilter always either sets TenantContext or rejects the
 * request with 403/400 before any controller/service code runs. */
class BeneficiarioServiceTest {

    private final BeneficiarioService service =
            new BeneficiarioService(
                    mock(BeneficiarioRepository.class),
                    mock(PessoaRepository.class),
                    mock(TenantSessionContext.class));

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void rejectsListingWhenNoTenantIsResolved() {
        assertThatThrownBy(() -> service.list(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
