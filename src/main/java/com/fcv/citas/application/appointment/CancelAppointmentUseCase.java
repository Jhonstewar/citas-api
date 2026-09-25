package com.fcv.citas.application.appointment;

import java.time.Clock;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.PatientDetail;
import com.fcv.citas.application.shared.Ownership;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.appointment.RescheduleRequestRepository;
import com.fcv.citas.domain.appointment.ReservationHolder;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-026: el paciente cancela una cita propia, futura y no terminal (RF-14, D16, D17).
 *
 * <p>Todo ocurre en UNA transaccion (CA-08): si cualquier paso falla no queda la cita cancelada con
 * la franja retenida, ni al reves, ni historial huerfano. La fila de la cita se bloquea primero, asi
 * que una cancelacion simultanea con una decision del ADMIN (o con otra cancelacion) se serializa y
 * la segunda ve el estado ya cambiado (409 {@code INVALID_TRANSITION}).</p>
 */
public class CancelAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final RescheduleRequestRepository reschedules;
    private final PatientAppointmentsUseCase patientAppointments;
    private final TransactionRunner tx;
    private final Clock clock;

    public CancelAppointmentUseCase(AppointmentRepository appointments, RescheduleRequestRepository reschedules,
            PatientAppointmentsUseCase patientAppointments, TransactionRunner tx, Clock clock) {
        this.appointments = appointments;
        this.reschedules = reschedules;
        this.patientAppointments = patientAppointments;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @param reason motivo opcional (≤ 500); vacio equivale a no darlo
     * @return el detalle actualizado, ya {@code CANCELLED}
     */
    public PatientDetail cancel(long patientUserId, long appointmentId, String reason) {
        tx.inTransaction(() -> {
            // 1. Ownership: ajena = inexistente (404, CA-06).
            Appointment appointment = Ownership.requireOwned(appointments.lockById(appointmentId),
                    Appointment::patientUserId, patientUserId, PatientAppointmentsUseCase.NOT_FOUND);
            // 2. Transicion de dominio: futura y no terminal; historial USER con el paciente (CA-07).
            Appointment.Transition transition = appointment.cancel(patientUserId, SystemZone.now(clock), reason);
            appointments.apply(transition);
            // 3. D18 y D37: una reprogramacion PENDING no tiene sentido sobre una cita cancelada. Se bloquea
            //    DESPUES de la cita (orden unico de bloqueo, ver RescheduleAppointmentUseCase).
            reschedules.lockPendingByAppointment(appointmentId)
                    .map(pending -> pending.cancelWithAppointment(patientUserId))
                    .ifPresent(reschedules::saveDecision);
            // 4. RN-09: liberar todas las reservas de la cita, incluida la retencion de la solicitud.
            if (transition.appointment().status().releasesSlots()) {
                appointments.releaseReservations(ReservationHolder.ofAppointment(appointmentId));
            }
            return null;
        });
        return patientAppointments.detail(patientUserId, appointmentId);
    }
}
