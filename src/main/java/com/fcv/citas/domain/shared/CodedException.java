package com.fcv.citas.domain.shared;

/**
 * Excepcion de negocio con un {@code code} estable que el cliente usa para decidir (contrato S3:
 * {@code contrato-rest-citas}). El mensaje va en español y se muestra tal cual al usuario, asi que
 * nunca incluye datos personales ni valores de la peticion.
 */
public abstract class CodedException extends DomainException {

    private final String code;

    protected CodedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
