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
     * aprobada. No es porque sea la única escritura de historial que se salta
     * {@link Appointment#transitionTo} —{@link Appointment#bookGeneral} y
     * {@link Appointment#requestSpecialized} también construyen su {@link StatusChange} directamente—,
     * sino por estos tres hechos:
     *
     * <ol>
     * <li>el historial se escribe solo por los dos métodos de escritura del puerto
     * {@link AppointmentRepository}: {@link AppointmentRepository#create create} y
     * {@link AppointmentRepository#apply apply};</li>
     * <li>la fila de {@code create} es, por contrato, la PRIMERA de la cita: ahí {@code previous} es
     * {@code null} y este método no deriva nada. Por ese camino pasan las dos fábricas, que devuelven
     * una cita recién nacida (sin id);</li>
     * <li>toda fila posterior viene de un {@link Appointment.Transition}, y solo {@link Appointment}
     * construye transiciones: {@link Appointment#rescheduleTo} repite el estado a propósito y el resto
     * pasa por {@code transitionTo}, que exige una transición declarada en
     * {@link AppointmentStatus#allowedNext()}, donde ninguna va de un estado a sí mismo.</li>
     * </ol>
     *
     * <p>Los tres tienen prueba: {@code HistoryRowInvariantTest} recorre por reflexión todos los
     * productores de {@code Transition} de {@code Appointment} (las fábricas nacen sin id; el estado
     * cambia siempre, salvo en {@code rescheduleTo}) y {@code HistoryWritersArchitectureTest} fija sobre
     * el bytecode que nadie fuera de {@code Appointment} construya un {@code StatusChange} ni una
     * {@code Transition}, ni inserte filas de historial fuera del adaptador del puerto.</p>
     *
     * @param previous estado de la fila anterior; {@code null} en la primera fila del historial
     * @param current  estado de la fila que se está leyendo
     * @return {@link #RESCHEDULED} o {@code null} si la fila es un cambio de estado normal
     */
    public static HistoryEvent between(AppointmentStatus previous, AppointmentStatus current) {
        return previous != null && previous == current ? RESCHEDULED : null;
    }
}
