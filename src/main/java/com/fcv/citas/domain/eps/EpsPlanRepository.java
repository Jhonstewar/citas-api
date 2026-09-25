package com.fcv.citas.domain.eps;

import java.util.Optional;

/** Puerto de escritura de planes de EPS (HU-012). */
public interface EpsPlanRepository {

    Optional<EpsPlan> findById(int id);

    boolean existsByCode(int epsId, String code);

    /** Nombre ya usado por otro plan de la misma EPS (misma collation que {@code uq_eps_plans_eps_name}). */
    boolean existsByName(int epsId, String name, Integer excludingId);

    /**
     * Inserta si {@code id} es nulo; si no, actualiza nombre, regimen y estado.
     *
     * @throws com.fcv.citas.domain.shared.DuplicateValueException si la base rechaza el codigo o el
     *         nombre por unicidad (carrera entre la comprobacion y la escritura)
     */
    EpsPlan save(EpsPlan plan);

    /**
     * Cierto si alguna afiliacion lo referencia, vigente o cerrada: el historial de afiliaciones
     * (D26) debe seguir apuntando a su plan, asi que no se borra (D28, 409 {@code PLAN_REFERENCED}).
     */
    boolean isReferenced(int id);

    void delete(int id);
}
