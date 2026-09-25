package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.appointment.AppointmentQueries.HistoryView;
import com.fcv.citas.application.appointment.AppointmentQueries.RescheduleView;
import com.fcv.citas.application.shared.Ownership;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentStatus;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-025: el paciente consulta sus citas y el detalle. El titular sale siempre del token; una cita
 * ajena responde igual que una inexistente (CA-07), via {@link Ownership}.
 */
public class PatientAppointmentsUseCase {

    /**
     * Cita con su historial y su ultima solicitud de reprogramacion; la usa el ADMIN (sin acciones del
     * paciente). {@code lastReschedule} es nulo si nunca hubo una.
     */
    public record Detail(AppointmentView appointment, List<HistoryView> history, RescheduleView lastReschedule) {
    }

    /**
     * {@code AppointmentDetail} del paciente (contrato S4): el detalle mas lo que el paciente puede
     * hacer ahora. Las banderas las decide el dominio, no el cliente ni el controlador.
     */
    public record PatientDetail(AppointmentView appointment, List<HistoryView> history, boolean cancellable,
            boolean reschedulable, RescheduleView lastReschedule) {
    }

    /** {@code Appointment} del listado del paciente con {@code cancellable} (aclaracion 8 de S4). */
    public record PatientAppointment(AppointmentView appointment, boolean cancellable) {
    }

    static final String NOT_FOUND = "La cita no existe";

    private final AppointmentQueries queries;
    private final Clock clock;

    public PatientAppointmentsUseCase(AppointmentQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public List<PatientAppointment> list(long patientUserId, String status, LocalDate date) {
        LocalDateTime now = SystemZone.now(clock);
        return queries.findByPatient(patientUserId, status, date).stream().map(v -> present(v, now)).toList();
    }

    /**
     * Una cita propia tal como la ve el paciente (tambien la respuesta de una reserva nueva). El
     * llamador ya garantizo que es suya: aqui solo se decide {@code cancellable}.
     */
    public PatientAppointment present(AppointmentView view) {
        return present(view, SystemZone.now(clock));
    }

    private static PatientAppointment present(AppointmentView view, LocalDateTime now) {
        return new PatientAppointment(view, toDomain(view).isCancellableAt(now));
    }

    public PatientDetail detail(long patientUserId, long appointmentId) {
        AppointmentView view = Ownership.requireOwned(queries.findById(appointmentId), v -> v.patient().id(),
                patientUserId, NOT_FOUND);
        Appointment appointment = toDomain(view);
        LocalDateTime now = SystemZone.now(clock);
        return new PatientDetail(view,
                queries.history(appointmentId).stream().map(PatientAppointmentsUseCase::forPatient).toList(),
                appointment.isCancellableAt(now),
                appointment.isReschedulableAt(now, view.pendingReschedule()),
                // HU-028: el motivo es el mismo dato que persistio el ADMIN (sin copia); ownership ya comprobado.
                queries.lastReschedule(appointmentId).orElse(null));
    }

    /**
     * La vista de lectura como cita de dominio, para preguntarle a ella sus reglas. La reutiliza la
     * agenda del profesional ({@code closable}, HU-020/HU-021).
     */
    static Appointment toDomain(AppointmentView v) {
        return new Appointment(v.id(), v.patient().id(), v.professional().id(), v.site().id(), v.specialty().id(),
                AppointmentStatus.valueOf(v.status()), v.date(), v.startTime(), v.endTime());
    }

    /**
     * El paciente ve quien decidio como rol, no como persona: el nombre del ADMIN es dato personal
     * del empleado y no le aporta nada (verificacion S3, F9). El ADMIN si ve el nombre completo.
     */
    private static HistoryView forPatient(HistoryView h) {
        return "ADMIN".equals(h.source())
                ? new HistoryView(h.status(), h.statusName(), h.source(), "Administración", h.reason(), h.changedAt())
                : h;
    }
}
