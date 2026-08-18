package com.tbm.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates the standard Brazilian CPF check-digit algorithm (11 digits, two check digits
 * computed from weighted sums) — a length/format-only regex is insufficient (research.md §3).
 */
public class CpfValidator implements ConstraintValidator<Cpf, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        if (!value.matches("\\d{11}")) {
            return false;
        }
        // Reject known-invalid sequences of 11 identical digits (e.g. "11111111111"), which
        // would otherwise pass the check-digit algorithm below.
        if (value.chars().distinct().count() == 1) {
            return false;
        }

        int[] digits = value.chars().map(c -> c - '0').toArray();
        int firstCheckDigit = computeCheckDigit(digits, 9);
        if (firstCheckDigit != digits[9]) {
            return false;
        }
        int secondCheckDigit = computeCheckDigit(digits, 10);
        return secondCheckDigit == digits[10];
    }

    private int computeCheckDigit(int[] digits, int length) {
        int sum = 0;
        int weight = length + 1;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * weight;
            weight--;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
