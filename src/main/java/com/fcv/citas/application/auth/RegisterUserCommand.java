package com.fcv.citas.application.auth;

/** Datos minimos de RF-01. {@code toString} no expone la contraseña. */
public record RegisterUserCommand(
        String firstNames,
        String lastNames,
        String documentTypeCode,
        String documentNumber,
        String email,
        String phone,
        String password) {

    @Override
    public String toString() {
        return "RegisterUserCommand[documentTypeCode=%s, password=***]".formatted(documentTypeCode);
    }
}
