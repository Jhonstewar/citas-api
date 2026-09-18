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

    record AppointmentTypeView(String code, String name, boolean requiresAdminApproval) {
    }

    record StatusView(String code, String name, boolean terminal) {
    }

    List<SiteView> sites();

    List<AppointmentTypeView> appointmentTypes();

    List<StatusView> appointmentStatuses();

    List<CodeName> documentTypes();

    List<CodeName> roles();

    List<CodeName> regimes();
}
