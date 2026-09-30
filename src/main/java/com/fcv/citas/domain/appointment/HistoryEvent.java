package com.fcv.citas.domain.appointment;

/**
 * D39 — evento que explica una fila del historial cuando el estado de la cita NO cambió. Existe
 * porque {@code appointment_status_history} solo guarda el estado nuevo (V3) y la reprogramación
 * aprobada mueve la cita sin moverla de estado: la fila repite {@code APPROVED} y, leída sola,
 * mentiría ("Aprobada" otra vez).
 *
 * <p>Se DERIVA, no se persiste: no hay columna ni migración nueva. La regla es una sola y vive aquí
 * ({@link #between}); el lado de lectura la aplica sobre el historial ya ordenado y el contrato REST
 * lo publica como {@code event?: 'RESCHEDULED'}, omitido cuando no aplica.</p>
 */
public enum HistoryEvent {

    /** Reprogramación aprobada (HU-031 CA-01 y CA-06): cambió la franja, no el estado. */
    RESCHEDULED;

    /**
     * D39: una fila que REPITE el estado de la fila anterior solo puede venir de una reprogramación
     * aprobada. Es la única escritura de historial que no pasa por {@link Appointment#transitionTo}, y
     * ninguna transición declarada en {@link AppointmentStatus#allowedNext()} va de un estado a sí
     * mismo, así que la derivación no puede confundirse con un cambio de estado real.
     *
     * @param previous estado de la fila anterior; {@code null} en la primera fila del historial
     * @param current  estado de la fila que se está leyendo
     * @return {@link #RESCHEDULED} o {@code null} si la fila es un cambio de estado normal
     */
    public static HistoryEvent between(AppointmentStatus previous, AppointmentStatus current) {
        return previous != null && previous == current ? RESCHEDULED : null;
    }
}
