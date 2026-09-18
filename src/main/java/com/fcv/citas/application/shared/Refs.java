package com.fcv.citas.application.shared;

/**
 * Referencias compactas que las vistas de lectura usan para nombrar sedes, especialidades y
 * personas sin exponer los agregados completos.
 */
public final class Refs {

    private Refs() {
    }

    public record SiteRef(int id, String code, String name) {
    }

    public record SpecialtyRef(int id, String code, String name, String appointmentType, int durationMinutes) {
    }

    public record PersonRef(long id, String fullName) {
    }

    /** Datos del paciente que solo ve el ADMIN al decidir (HU-029 CA-04). */
    public record PatientRef(long id, String fullName, String documentType, String documentNumber, String email,
            String phone) {
    }
}
