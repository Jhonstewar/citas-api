package com.fcv.citas.domain.user;

import com.fcv.citas.domain.shared.DomainException;

public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException() {
        super("El email ya está registrado");
    }
}
