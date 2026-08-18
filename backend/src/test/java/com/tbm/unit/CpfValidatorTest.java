package com.tbm.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tbm.common.validation.CpfValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfValidatorTest {

    private final CpfValidator validator = new CpfValidator();

    @ParameterizedTest
    @ValueSource(strings = {"92239444657", "75678428403", "06433433189", "81018804293"})
    void acceptsValidCpfs(String cpf) {
        assertThat(validator.isValid(cpf, null)).isTrue();
    }

    @Test
    void rejectsWrongCheckDigit() {
        assertThat(validator.isValid("92239444658", null)).isFalse();
    }

    @Test
    void rejectsTooShort() {
        assertThat(validator.isValid("123456789", null)).isFalse();
    }

    @Test
    void rejectsNonDigits() {
        assertThat(validator.isValid("9223944465a", null)).isFalse();
    }

    @Test
    void rejectsAllIdenticalDigits() {
        assertThat(validator.isValid("11111111111", null)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
