package com.fcv.citas.domain.appointment;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de escritura de citas, su historial y el libro de reservas de slots (dec-003).
 * Cada metodo se llama dentro de la transaccion del caso de uso.
 */
public interface AppointmentRepository {

    /** Inserta la cita y su primera fila de historial; devuelve la cita con id. */
    Appointment create(Appointment appointment, StatusChange initial);

    /**
     * Reserva los slots para la cita, en orden ({@code slot_order} 1 y 2).
     *
     * @throws com.fcv.citas.domain.shared.ConflictException {@code SLOT_TAKEN} si algun slot ya esta
     *         reservado o retenido: la clave primaria de {@code slot_reservations} lo impide (RN-01)
     */
    void reserveSlots(long appointmentId, List<Long> slotIds);

    /** Lee la cita bloqueando su fila hasta el fin de la transaccion (decisiones concurrentes). */
    Optional<Appointment> lockById(long appointmentId);

    /**
     * Guarda estado, dia, horas y sede de la cita y agrega la fila de historial, en la misma transaccion
     * (HU-032 CA-05). Dia, horas y sede solo cambian al aprobar una reprogramacion (HU-031).
     */
    void apply(Appointment.Transition transition);

    /**
     * RN-09: UNICO camino de liberacion de franjas. Borra las filas de {@code slot_reservations} del
     * titular (ver {@link ReservationHolder}), con lo que los slots vuelven a ofrecerse. Lo usan
     * cancelar y rechazar una cita, rechazar una reprogramacion y, al aprobarla, liberar la franja antigua.
     */
    void releaseReservations(ReservationHolder holder);

    /**
     * HU-027: retiene los slots propuestos para la solicitud ({@code reservation_type =
     * 'RESCHEDULE_REQUEST'}, {@code slot_order} 1 y 2). Es la MISMA barrera que {@link #reserveSlots}: la
     * clave primaria de {@code slot_reservations} (dec-003), asi que compite con cualquier reserva o
     * retencion concurrente (RN-01).
     *
     * @throws com.fcv.citas.domain.shared.ConflictException {@code SLOT_TAKEN} si algun slot ya esta tomado
     */
    void holdForReschedule(long rescheduleRequestId, List<Long> slotIds);

    /**
     * HU-031 (aprobar): convierte la retencion de la solicitud en ocupacion de la cita ACTUALIZANDO las
     * filas ({@code RESCHEDULE_REQUEST} → {@code APPOINTMENT}, titular = la cita), conservando
     * {@code slot_id} y {@code slot_order}. No borra ni reinserta: el slot nunca queda libre, ni un
     * instante, para otra reserva (RN-01). Requiere haber liberado antes la franja antigua de la cita
     * ({@link ReservationHolder#ofCurrentSlot}), porque {@code (appointment_id, slot_order)} es unico.
     */
    void convertHeldToAppointment(long rescheduleRequestId, long appointmentId);
}
