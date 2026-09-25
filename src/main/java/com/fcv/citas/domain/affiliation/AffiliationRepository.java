package com.fcv.citas.domain.affiliation;

import java.util.Optional;

/** Puerto de salida para persistir afiliaciones. */
public interface AffiliationRepository {

    /** Persiste una afiliacion nueva y devuelve la instancia con su id. */
    Affiliation saveNew(Affiliation affiliation);

    /**
     * La afiliacion vigente del usuario, bloqueando hasta el fin de la transaccion al usuario y a su
     * afiliacion: dos cambios simultaneos del mismo usuario se serializan (D26: una sola vigente).
     */
    Optional<Affiliation> lockCurrent(long userId);

    /** Guarda el cierre de una afiliacion ({@link Affiliation#close}): no vigente y con {@code ended_on}. */
    void close(Affiliation closed);
}
