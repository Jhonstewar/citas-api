package com.fcv.citas.domain.shared;

/**
 * Datos que el dominio no acepta (HTTP 400). Si {@code field} no es nulo, el error se atribuye a
 * ese campo del cuerpo ({@code fieldErrors}).
 */
public class InvalidRequestException extends CodedException {

    private final String field;

    public InvalidRequestException(String code, String field, String message) {
        super(code, message);
        this.field = field;
    }

    public static InvalidRequestException field(String field, String message) {
        return new InvalidRequestException("VALIDATION", field, message);
    }

    public String field() {
        return field;
    }
}
