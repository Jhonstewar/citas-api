package com.fcv.citas.domain.eps;

import java.util.Optional;

/** Puerto de escritura de EPS (HU-012). Las lecturas con conteos van por {@code EpsQueries} (D14). */
public interface EpsRepository {

    Optional<Eps> findById(int id);

    boolean existsByCode(String code);

    /**
     * Nombre ya usado por otra EPS, con la misma collation que la unica {@code uq_eps_name} (sin
     * distinguir mayusculas ni tildes); {@code excludingId} opcional.
     */
    boolean existsByName(String name, Integer excludingId);

    /**
     * Inserta si {@code id} es nulo; si no, actualiza nombre y estado.
     *
     * @throws com.fcv.citas.domain.shared.DuplicateValueException si la base rechaza el codigo o el
     *         nombre por unicidad (carrera entre la comprobacion y la escritura)
     */
    Eps save(Eps eps);

    /** Cierto si tiene algun plan: entonces no se borra (D28, 409 {@code EPS_REFERENCED}). */
    boolean hasPlans(int id);

    void delete(int id);
}
