package com.fcv.citas.application.catalog;

import java.util.List;

/**
 * Puerto de lectura de los catalogos fijos (RF-05, HU-010). Solo lectura: los catalogos fijos se
 * siembran por migracion y la API no los modifica.
 */
public interface CatalogQueries {

    record SiteView(int id, String code, String name, String address, String city, String department) {
    }

    record CodeName(String code, String name) {
    }

    /** Referencia compacta a una fila de catalogo, con su id. */
    record CatalogRef(int id, String code, String name) {
    }

    /**
     * HU-009: plan de EPS ofrecido en el registro. La EPS y el regimen se derivan del plan y no
     * se almacenan repetidos en la afiliacion.
     */
    record InsurancePlanView(int id, String code, String name, CatalogRef eps, CatalogRef regime) {
    }

    record AppointmentTypeView(String code, String name, boolean requiresAdminApproval) {
    }

    record StatusView(String code, String name, boolean terminal) {
    }

    List<SiteView> sites();

    List<AppointmentTypeView> appointmentTypes();

    List<StatusView> appointmentStatuses();

    /** HU-010 CA-04: incluye PENDING, estado inicial de toda reprogramacion (RF-15, S4). */
    List<StatusView> rescheduleStatuses();

    List<CodeName> documentTypes();

    List<CodeName> roles();

    List<CodeName> regimes();

    /**
     * HU-009: planes activos de EPS activas, ordenados por EPS y luego por plan. Es la unica
     * lectura de catalogo publica: la consume el formulario de registro, que aun no tiene sesion.
     */
    List<InsurancePlanView> insurancePlans();
}
