package com.fcv.citas.domain.catalog;

/**
 * Tipo de cita y su politica de aprobacion (RN-02, RN-03). Coincide con
 * {@code appointment_types.code}: la general se aprueba sola; la especializada requiere ADMIN.
 */
public enum AppointmentType {
    GENERAL(false),
    SPECIALIZED(true);

    private final boolean requiresAdminApproval;

    AppointmentType(boolean requiresAdminApproval) {
        this.requiresAdminApproval = requiresAdminApproval;
    }

    public boolean requiresAdminApproval() {
        return requiresAdminApproval;
    }
}
