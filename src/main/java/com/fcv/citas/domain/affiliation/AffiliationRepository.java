package com.fcv.citas.domain.affiliation;

/** Puerto de salida para persistir afiliaciones. */
public interface AffiliationRepository {

    /** Persiste una afiliacion nueva y devuelve la instancia con su id. */
    Affiliation saveNew(Affiliation affiliation);
}
