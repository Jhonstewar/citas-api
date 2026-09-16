package com.fcv.citas.domain.user;

import com.fcv.citas.domain.shared.DomainException;

public class DocumentAlreadyRegisteredException extends DomainException {

    public DocumentAlreadyRegisteredException() {
        super("El documento ya está registrado");
    }
}
