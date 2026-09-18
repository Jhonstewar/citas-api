package com.fcv.citas.infrastructure.rest.professional;

import java.util.List;

import com.fcv.citas.application.professional.ProfessionalQueries.AssignedSpecialtyView;
import com.fcv.citas.application.professional.ProfessionalQueries.ProfessionalView;
import com.fcv.citas.application.shared.Refs.SiteRef;

/** {@code Professional} del contrato S3. Nunca incluye la contraseña ni su hash (HU-013 CA-01). */
public record ProfessionalResponse(long id, long userId, String firstNames, String lastNames, String fullName,
        String documentType, String documentNumber, String email, String phone, String professionalCode,
        String licenseNumber, boolean active, List<AssignedSpecialtyView> specialties, List<SiteRef> sites) {

    public static ProfessionalResponse from(ProfessionalView v) {
        return new ProfessionalResponse(v.id(), v.userId(), v.firstNames(), v.lastNames(), v.fullName(),
                v.documentType(), v.documentNumber(), v.email(), v.phone(), v.professionalCode(),
                v.licenseNumber(), v.active(), v.specialties(), v.sites());
    }
}
