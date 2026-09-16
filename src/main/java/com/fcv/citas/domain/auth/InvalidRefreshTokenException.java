package com.fcv.citas.domain.auth;

import com.fcv.citas.domain.shared.DomainException;

/** Refresh token inexistente, expirado, revocado o reutilizado: respuesta uniforme (HU-003). */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super("La sesión no es válida o ha expirado");
    }
}
