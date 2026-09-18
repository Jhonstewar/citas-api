package com.fcv.citas.infrastructure.rest.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.catalog.CatalogQueries;
import com.fcv.citas.application.catalog.CatalogQueries.AppointmentTypeView;
import com.fcv.citas.application.catalog.CatalogQueries.CodeName;
import com.fcv.citas.application.catalog.CatalogQueries.SiteView;
import com.fcv.citas.application.catalog.CatalogQueries.StatusView;
import com.fcv.citas.application.catalog.ManageSpecialtiesUseCase;

/**
 * HU-010: catalogos de solo lectura para cualquier rol autenticado. Sin metodos de escritura: un
 * POST/PUT/DELETE aqui responde 405 (CA-06). Las especialidades expuestas son solo las activas.
 */
@RestController
@RequestMapping("/api/catalogs")
class CatalogController {

    private final CatalogQueries catalogs;
    private final ManageSpecialtiesUseCase specialties;

    CatalogController(CatalogQueries catalogs, ManageSpecialtiesUseCase specialties) {
        this.catalogs = catalogs;
        this.specialties = specialties;
    }

    @GetMapping("/sites")
    List<SiteView> sites() {
        return catalogs.sites();
    }

    @GetMapping("/specialties")
    List<SpecialtyResponse> activeSpecialties() {
        return specialties.listActive().stream().map(SpecialtyResponse::from).toList();
    }

    @GetMapping("/appointment-types")
    List<AppointmentTypeView> appointmentTypes() {
        return catalogs.appointmentTypes();
    }

    @GetMapping("/appointment-statuses")
    List<StatusView> appointmentStatuses() {
        return catalogs.appointmentStatuses();
    }

    @GetMapping("/document-types")
    List<CodeName> documentTypes() {
        return catalogs.documentTypes();
    }

    @GetMapping("/roles")
    List<CodeName> roles() {
        return catalogs.roles();
    }

    @GetMapping("/regimes")
    List<CodeName> regimes() {
        return catalogs.regimes();
    }
}
