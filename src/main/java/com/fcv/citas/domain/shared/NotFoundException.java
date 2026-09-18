package com.fcv.citas.domain.shared;

/**
 * El recurso no existe o no pertenece a quien lo pide (HTTP 404). Se usa lo mismo en ambos casos
 * para no revelar la existencia de recursos ajenos (HU-005 CA-05, HU-025 CA-07).
 */
public class NotFoundException extends CodedException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
