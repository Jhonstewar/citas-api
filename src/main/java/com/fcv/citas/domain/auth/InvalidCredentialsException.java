package com.fcv.citas.domain.auth;

import com.fcv.citas.domain.shared.DomainException;

/** Credenciales invalidas o cuenta inactiva: mensaje unico que no revela la causa (HU-002 CA-03). */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
