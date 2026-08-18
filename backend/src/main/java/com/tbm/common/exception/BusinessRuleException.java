package com.tbm.common.exception;

/**
 * A foreseeable business-rule violation that should surface as a 400 Problem Detail, distinct
 * from a Bean Validation failure (e.g., "the referenced Pessoa does not exist").
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
