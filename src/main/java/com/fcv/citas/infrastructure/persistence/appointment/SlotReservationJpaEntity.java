package com.fcv.citas.infrastructure.persistence.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.domain.Persistable;

/**
 * Tabla {@code slot_reservations}: libro unico de ocupacion con PK {@code slot_id} (dec-003).
 *
 * <p>Implementa {@link Persistable} con {@code isNew() = true}: el id viene asignado (es el slot),
 * y sin esto Spring Data haria {@code merge}, que consulta primero y ante un slot ya reservado
 * ACTUALIZARIA la fila ajena en vez de fallar. Con {@code persist} el INSERT choca con la PK y la
 * doble reserva es imposible (RN-01).</p>
 */
@Entity
@Table(name = "slot_reservations")
public class SlotReservationJpaEntity implements Persistable<Long> {

    @Id
    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "reservation_type", nullable = false, columnDefinition = "ENUM('APPOINTMENT','RESCHEDULE_REQUEST')")
    private String reservationType;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "reschedule_request_id")
    private Long rescheduleRequestId;

    @Column(name = "slot_order", nullable = false)
    private short slotOrder;

    protected SlotReservationJpaEntity() {
    }

    public static SlotReservationJpaEntity forAppointment(long slotId, long appointmentId, int order) {
        SlotReservationJpaEntity e = new SlotReservationJpaEntity();
        e.slotId = slotId;
        e.reservationType = "APPOINTMENT";
        e.appointmentId = appointmentId;
        e.slotOrder = (short) order;
        return e;
    }

    /** HU-027: retencion de la franja propuesta por una solicitud de reprogramacion PENDING. */
    public static SlotReservationJpaEntity forRescheduleRequest(long slotId, long rescheduleRequestId, int order) {
        SlotReservationJpaEntity e = new SlotReservationJpaEntity();
        e.slotId = slotId;
        e.reservationType = "RESCHEDULE_REQUEST";
        e.rescheduleRequestId = rescheduleRequestId;
        e.slotOrder = (short) order;
        return e;
    }

    @Override
    public Long getId() {
        return slotId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
