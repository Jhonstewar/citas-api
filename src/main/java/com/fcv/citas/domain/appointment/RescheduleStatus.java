package com.fcv.citas.domain.appointment;

/**
 * Estados de una solicitud de reprogramacion (RF-15). Coincide con {@code reschedule_statuses.code}
 * (V4). Solo {@code PENDING} admite una decision; los demas son terminales.
 */
public enum RescheduleStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
