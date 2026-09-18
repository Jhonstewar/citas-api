package com.fcv.citas.application.professional;

import java.util.List;

/** Alta de profesional por ADMIN (HU-013). {@code toString} no expone la contraseña inicial. */
public record CreateProfessionalCommand(
        String firstNames,
        String lastNames,
        String documentTypeCode,
        String documentNumber,
        String email,
        String phone,
        String password,
        String professionalCode,
        String licenseNumber,
        List<Integer> specialtyIds,
        Integer primarySpecialtyId,
        List<Integer> siteIds) {

    @Override
    public String toString() {
        return "CreateProfessionalCommand[professionalCode=%s, password=***]".formatted(professionalCode);
    }
}
