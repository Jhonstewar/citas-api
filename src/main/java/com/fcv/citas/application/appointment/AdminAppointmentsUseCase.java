package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.util.List;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.appointment.AppointmentQueries.InboxFilter;
import com.fcv.citas.application.appointment.AppointmentQueries.Summary;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.Detail;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-029 (bandeja) y HU-030 (aprobar o rechazar una cita especializada). Cada decision bloquea la
 * fila de la cita: dos decisiones simultaneas se serializan y la segunda ve el estado ya cambiado
 * (409 INVALID_TRANSITION, CA-07). Estado, historial y liberacion de slots van en la misma transaccion.
 */
public class AdminAppointmentsUseCase {

    private final AppointmentRepository appointments;
    private final AppointmentQueries queries;
    private final TransactionRunner tx;
    private final Clock clock;

    public AdminAppointmentsUseCase(AppointmentRepository appointments, AppointmentQueries queries,
            TransactionRunner tx, Clock clock) {
        this.appointments = appointments;
        this.queries = queries;
        this.tx = tx;
        this.clock = clock;
    }

    public List<AppointmentView> inbox(InboxFilter filter) {
        return queries.findPendingRequests(filter);
    }

    public Detail detail(long appointmentId) {
        AppointmentView view = queries.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("La cita no existe"));
        return new Detail(view, queries.history(appointmentId));
    }

    public Summary summary() {
        return queries.summary(SystemZone.now(clock).toLocalDate());
    }

    /** HU-030 CA-01: las reservas de slots se conservan. */
    public Detail approve(long adminUserId, long appointmentId) {
        tx.inTransaction(() -> {
            Appointment appointment = locked(appointmentId);
            appointments.apply(appointment.approve(adminUserId, SystemZone.now(clock)));
            return null;
        });
        return detail(appointmentId);
    }

    /** HU-030 CA-02 y CA-03 (RN-04, RN-09): motivo obligatorio; libera los slots. */
    public Detail reject(long adminUserId, long appointmentId, String reason) {
        tx.inTransaction(() -> {
            Appointment.Transition transition = locked(appointmentId).reject(adminUserId, reason);
            appointments.apply(transition);
            if (transition.appointment().status().releasesSlots()) {
                appointments.releaseSlots(appointmentId);
            }
            return null;
        });
        return detail(appointmentId);
    }

    private Appointment locked(long appointmentId) {
        return appointments.lockById(appointmentId).orElseThrow(() -> new NotFoundException("La cita no existe"));
    }
}
