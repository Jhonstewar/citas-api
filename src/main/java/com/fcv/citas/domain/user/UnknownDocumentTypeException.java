package com.fcv.citas.domain.user;

import com.fcv.citas.domain.shared.DomainException;

public class UnknownDocumentTypeException extends DomainException {

    public UnknownDocumentTypeException() {
        super("El tipo de documento no existe en el catálogo");
    }
}
