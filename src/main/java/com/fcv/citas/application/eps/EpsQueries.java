package com.fcv.citas.application.eps;

import java.util.List;
import java.util.Optional;

import com.fcv.citas.application.catalog.CatalogQueries.CatalogRef;

/**
 * Puerto de lectura del catalogo de EPS para el ADMIN (HU-012), con SQL plano como el resto de
 * lecturas (D14). Incluye activas e inactivas; la oferta publica sigue siendo
 * {@code CatalogQueries#insurancePlans}.
 */
public interface EpsQueries {

    /** {@code Eps} del contrato S4. */
    record EpsView(int id, String code, String name, boolean active, int planCount) {
    }

    /** {@code EpsPlan} del contrato S4: el regimen como {@code {id, code, name}} (aclaracion 4). */
    record EpsPlanView(int id, int epsId, String code, String name, boolean active, CatalogRef regime) {
    }

    /** Todas, por nombre. */
    List<EpsView> findAll();

    Optional<EpsView> findById(int id);

    /** Planes de la EPS, por nombre. */
    List<EpsPlanView> plansOf(int epsId);

    Optional<EpsPlanView> findPlan(int id);
}
