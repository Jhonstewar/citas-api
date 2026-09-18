package com.fcv.citas.application.professional;

import java.util.List;
import java.util.Optional;

import com.fcv.citas.application.shared.Refs.SiteRef;

/** Puerto de lectura de profesionales con sus datos de usuario, especialidades y sedes. */
public interface ProfessionalQueries {

    record AssignedSpecialtyView(int id, String code, String name, String appointmentType, int durationMinutes,
            boolean active, boolean primary) {
    }

    record ProfessionalView(long id, long userId, String firstNames, String lastNames, String documentType,
            String documentNumber, String email, String phone, String professionalCode, String licenseNumber,
            boolean active, List<AssignedSpecialtyView> specialties, List<SiteRef> sites) {

        public String fullName() {
            return firstNames + " " + lastNames;
        }
    }

    /** Filtros opcionales del listado de ADMIN: {@code null} = sin filtrar. */
    record Filter(Boolean active, Integer specialtyId, Integer siteId) {
    }

    List<ProfessionalView> list(Filter filter);

    Optional<ProfessionalView> findById(long id);

    Optional<ProfessionalView> findByUserId(long userId);
}
