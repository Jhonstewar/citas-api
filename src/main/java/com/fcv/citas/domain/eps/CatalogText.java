package com.fcv.citas.domain.eps;

import java.util.Locale;

import com.fcv.citas.domain.shared.InvalidRequestException;

/** Validacion y normalizacion de codigos y nombres del catalogo de EPS (400 con el campo). */
final class CatalogText {

    private CatalogText() {
    }

    /** Texto obligatorio, recortado y de a lo sumo {@code max} caracteres. */
    static String required(String value, String field, String label, int max) {
        if (value == null || value.isBlank()) {
            throw InvalidRequestException.field(field, label + " es obligatorio");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw InvalidRequestException.field(field, label + " admite como máximo " + max + " caracteres");
        }
        return trimmed;
    }

    /** Codigo estable: mayusculas y sin espacios, como los de especialidades ({@code Specialty}). */
    static String code(String value, String field, String label, int max) {
        return required(value, field, label, max).toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
