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

    /** Guarda el nuevo estado y agrega la fila de historial, en la misma transaccion (HU-032 CA-05). */
    void apply(Appointment.Transition transition);

    /** RN-09: borra las reservas de slots de la cita. */
    void releaseSlots(long appointmentId);
}
