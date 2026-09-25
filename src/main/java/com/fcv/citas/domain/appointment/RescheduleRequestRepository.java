package com.fcv.citas.domain.appointment;

import java.util.Optional;

/**
 * Puerto de escritura de las solicitudes de reprogramacion ({@code reschedule_requests}). La
 * retencion de slots NO pasa por aqui: vive en el libro unico ({@link AppointmentRepository}).
 *
 * <p>Orden de bloqueo para no interbloquear: siempre la CITA primero ({@link AppointmentRepository#lockById})
 * y despues la solicitud. Lo siguen pedir, aprobar, rechazar y cancelar.</p>
 */
public interface RescheduleRequestRepository {

    /**
     * Inserta una solicitud {@code PENDING} y la devuelve con id.
     *
     * @throws com.fcv.citas.domain.shared.ConflictException {@code RESCHEDULE_PENDING} si la cita ya
     *         tiene otra sin decidir: lo impide {@code uq_reschedule_requests_active} (V3), no solo el codigo
     */
    RescheduleRequest create(RescheduleRequest request);

    Optional<RescheduleRequest> findById(long requestId);

    /** Lee la solicitud bloqueando su fila hasta el fin de la transaccion. */
    Optional<RescheduleRequest> lockById(long requestId);

    /** La solicitud sin decidir de la cita, bloqueada (como mucho una, D20). */
    Optional<RescheduleRequest> lockPendingByAppointment(long appointmentId);

    /** Guarda estado, decisor, motivo y fecha de decision (la pone la base). */
    void saveDecision(RescheduleRequest decided);
}
