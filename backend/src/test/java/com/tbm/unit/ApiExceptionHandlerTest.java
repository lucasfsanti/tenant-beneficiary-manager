package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** handleUnexpected is the last-resort 500 mapping — no legitimate request through the running
 * app should ever trigger a truly unexpected exception, so it is exercised directly here rather
 * than by contriving a real server failure. */
class ApiExceptionHandlerTest {

    @Test
    void mapsAnUnexpectedExceptionToAGeneric500() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Erro interno");
        assertThat(problem.getDetail()).isEqualTo("Ocorreu um erro inesperado.");
    }
}
