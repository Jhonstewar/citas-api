package com.fcv.citas.domain.appointment;

/**
 * Titular de reservas en el libro unico {@code slot_reservations} (dec-003), para el UNICO camino de
 * liberacion de franjas {@link AppointmentRepository#releaseReservations} (RN-09, riesgo de
 * {@code PLAN_RETOMA_S4.md} §5: "tres caminos que liberan slots divergen").
 *
 * <ul>
 *   <li>{@link #ofAppointment}: todo lo que la cita tiene tomado, es decir sus reservas
 *       {@code APPOINTMENT} y las retenciones {@code RESCHEDULE_REQUEST} de sus solicitudes de
 *       reprogramacion. Lo usan cancelar (HU-026, con D18) y rechazar una cita (HU-030).</li>
 *   <li>{@link #ofRescheduleRequest}: solo la retencion de una solicitud; la cita conserva su
 *       franja. Lo usa rechazar una reprogramacion (HU-031).</li>
 *   <li>{@link #ofCurrentSlot}: solo las reservas {@code APPOINTMENT} de la cita, su franja vigente,
 *       sin tocar la retencion de su solicitud. Lo usa aprobar una reprogramacion (HU-031): primero se
 *       libera la franja antigua y despues la retencion se CONVIERTE en ocupacion de la cita
 *       ({@link AppointmentRepository#convertHeldToAppointment}), sin borrarla.</li>
 * </ul>
 */
public record ReservationHolder(Kind kind, long id) {

    public enum Kind {
        APPOINTMENT,
        APPOINTMENT_CURRENT_SLOT,
        RESCHEDULE_REQUEST
    }

    public static ReservationHolder ofAppointment(long appointmentId) {
        return new ReservationHolder(Kind.APPOINTMENT, appointmentId);
    }

    public static ReservationHolder ofCurrentSlot(long appointmentId) {
        return new ReservationHolder(Kind.APPOINTMENT_CURRENT_SLOT, appointmentId);
    }

    public static ReservationHolder ofRescheduleRequest(long rescheduleRequestId) {
        return new ReservationHolder(Kind.RESCHEDULE_REQUEST, rescheduleRequestId);
    }

    public ReservationHolder {
        java.util.Objects.requireNonNull(kind, "kind");
    }
}
