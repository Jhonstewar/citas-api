package com.fcv.citas.domain.shared;

/** Base de las excepciones de negocio. Sin dependencias de framework. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
