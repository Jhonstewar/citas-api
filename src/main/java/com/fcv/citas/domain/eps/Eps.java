package com.fcv.citas.domain.eps;

/**
 * EPS del catalogo configurable (RF-06, HU-012). Se activa y desactiva como operaciones del
 * dominio; desactivarla retira sus planes de la oferta sin tocar sus planes ni las afiliaciones
 * existentes (CA-05): el predicado "plan ofrecible" exige plan activo Y EPS activa.
 *
 * <p>El codigo es inmutable (lo usan semillas e integraciones); el nombre se puede corregir. Ambos
 * son unicos: la comprobacion la hace el caso de uso y la garantia final la base (V2, V9).</p>
 */
public record Eps(Integer id, String code, String name, boolean active) {

    /** Limites de las columnas {@code eps.code} y {@code eps.name} (V2). */
    public static final int MAX_CODE = 20;
    public static final int MAX_NAME = 160;

    public Eps {
        code = CatalogText.code(code, "code", "El código", MAX_CODE);
        name = CatalogText.required(name, "name", "El nombre", MAX_NAME);
    }

    /** Alta: nace activa. */
    public static Eps create(String code, String name) {
        return new Eps(null, code, name, true);
    }

    public Eps rename(String newName) {
        return new Eps(id, code, newName, active);
    }

    public Eps activate() {
        return new Eps(id, code, name, true);
    }

    public Eps deactivate() {
        return new Eps(id, code, name, false);
    }
}
