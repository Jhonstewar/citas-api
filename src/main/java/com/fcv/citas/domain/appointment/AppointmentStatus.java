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

    /** RN-09: rechazar o cancelar libera las reservas de slots. */
    public boolean releasesSlots() {
        return this == REJECTED || this == CANCELLED;
    }
}
