package com.fcv.citas.domain.shared;

/** Un valor que debe ser unico ya existe (HTTP 409, {@code code=DUPLICATE}, con el campo). */
public class DuplicateValueException extends ConflictException {

    private final String field;

    public DuplicateValueException(String field, String message) {
        super("DUPLICATE", message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
