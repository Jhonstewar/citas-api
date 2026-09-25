package com.fcv.citas.application.affiliation;

import java.time.LocalDate;
import java.util.Optional;

import com.fcv.citas.application.catalog.CatalogQueries.InsurancePlanView;

/**
 * Puerto de lectura de la afiliacion (HU-009). El plan se devuelve con el MISMO cuerpo que el
 * catalogo publico, con su EPS y su regimen derivados (3FN, CA-02), aunque el plan o la EPS esten
 * ya desactivados: una afiliacion declarada sigue mostrando lo que se declaro (HU-012 CA-05).
 */
public interface AffiliationQueries {

    /** {@code Affiliation} del contrato S4 de identidad. */
    record AffiliationView(long id, InsurancePlanView plan, LocalDate startedOn) {
    }

    /** La afiliacion vigente del usuario, si la tiene. */
    Optional<AffiliationView> current(long userId);

    Optional<AffiliationView> findById(long affiliationId);
}
