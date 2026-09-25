package com.fcv.citas.domain.appointment;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de una cita y sus transiciones permitidas (RN-11). Coincide con
 * {@code appointment_statuses.code}. Cualquier transicion no listada se rechaza en el dominio.
 */
public enum AppointmentStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    /** Destinos validos desde cada estado. Los terminales no tienen salida. */
    public Set<AppointmentStatus> allowedNext() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED -> EnumSet.of(CANCELLED, COMPLETED, NO_SHOW);
            case REJECTED, CANCELLED, COMPLETED, NO_SHOW -> EnumSet.noneOf(AppointmentStatus.class);
        };
    }

    public boolean canTransitionTo(AppointmentStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Terminal = sin salida en la tabla de transiciones (RF-14: "una cita cancelada no se reactiva").
     * Se deriva de {@link #allowedNext()} para que no existan dos listas; {@code is_terminal} del
     * catalogo (V4) debe coincidir, y {@code S3DebtIntegrationTest} lo comprueba (R5).
     */
    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /**
     * RN-09: rechazar o cancelar libera las reservas de slots. Debe coincidir con
     * {@code appointment_statuses.releases_slots} (V4); {@code S3DebtIntegrationTest} lo comprueba (R5).
     */
    public boolean releasesSlots() {
        return this == REJECTED || this == CANCELLED;
    }
}
