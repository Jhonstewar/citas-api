package com.fcv.citas.domain.shared;

/** Una regla de negocio del PRD impide la operacion (HTTP 422): RN-06, RN-07, RN-08... */
public class BusinessRuleException extends CodedException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
