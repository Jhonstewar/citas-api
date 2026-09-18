package com.fcv.citas.infrastructure.rest.catalog;

import com.fcv.citas.domain.catalog.Specialty;

/**
 * Especialidad en la API. {@code requiresAdminApproval} se deriva del tipo de cita, no de otra
 * configuracion (HU-011 CA-03).
 */
public record SpecialtyResponse(
        int id,
        String code,
        String name,
        String appointmentType,
        boolean requiresAdminApproval,
        int durationMinutes,
        boolean active,
        @com.fasterxml.jackson.annotation.JsonProperty("protected") boolean isProtected) {

    public static SpecialtyResponse from(Specialty s) {
        return new SpecialtyResponse(s.id(), s.code(), s.name(), s.appointmentType().name(),
                s.appointmentType().requiresAdminApproval(), s.durationMinutes(), s.active(), s.isProtected());
    }
}
