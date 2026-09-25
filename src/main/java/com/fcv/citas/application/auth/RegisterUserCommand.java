package com.fcv.citas.application.auth;

/**
 * Datos minimos de RF-01. {@code toString} no expone la contraseña.
 *
 * <p>{@code insurancePlanId} es opcional (HU-009): si es nulo el registro no crea afiliacion y se
 * comporta exactamente como antes.</p>
 */
public record RegisterUserCommand(
        String firstNames,
        String lastNames,
        String documentTypeCode,
        String documentNumber,
        String email,
        String phone,
        String password,
        Integer insurancePlanId) {

    @Override
    public String toString() {
        return "RegisterUserCommand[documentTypeCode=%s, password=***]".formatted(documentTypeCode);
    }
}
