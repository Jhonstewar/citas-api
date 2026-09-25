package com.fcv.citas.domain.affiliation;

/**
 * Puerto de lectura del catalogo de planes de EPS, en la misma linea que
 * {@code DocumentTypeCatalog}. Lo implementa el mismo adaptador que lista los planes, para que lo
 * que el servidor acepta y lo que el formulario ofrece no puedan divergir (HU-009).
 */
public interface InsurancePlanCatalog {

    /** {@code true} solo si el plan existe, esta activo y su EPS esta activa (RF-06). */
    boolean isSelectable(int epsPlanId);
}
