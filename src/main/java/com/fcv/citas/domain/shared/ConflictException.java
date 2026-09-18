package com.fcv.citas.domain.shared;

/** El estado actual impide la operacion (HTTP 409): franja tomada, transicion invalida... */
public class ConflictException extends CodedException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
