package com.fcv.citas.domain.auth;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Token de recuperacion inexistente, caducado, ya usado o revocado: UNA sola respuesta
 * ({@code 400 RESET_TOKEN_INVALID}) que no distingue el motivo (HU-007 CA-02..CA-04).
 */
public class InvalidResetTokenException extends InvalidRequestException {

    public static final String CODE = "RESET_TOKEN_INVALID";

    public InvalidResetTokenException() {
        super(CODE, null, "El enlace para restablecer la contraseña no es válido o ya expiró; solicita uno nuevo");
    }
}
