package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiFunction;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.shared.Ownership;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.professional.Professional;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-020 (agenda de citas aprobadas) y HU-021 (cierre de atencion) del profesional autenticado.
 *
 * <p>El titular sale siempre del token (userId → su perfil profesional); una cita de otro
 * profesional responde igual que una inexistente, via {@link Ownership} (HU-021 CA-03, HU-005).
 * Cuando se puede cerrar lo decide solo {@link Appointment#isClosableAt} (D19); este caso de uso no
 * repite la regla.</p>
 */
public class ProfessionalAppointmentsUseCase {

    /**
     * D35 / RF-16: lo minimo que identifica al paciente en la atencion. Ni email, ni telefono, ni
     * afiliacion: la proyeccion se decide aqui, no en el adaptador REST (HU-020 CA-06).
     */
    public record ProfessionalPatientRef(String fullName, String documentType, String documentNumber) {
    }

    /** {@code ProfessionalAppointment} del contrato S4: la cita, su paciente minimo y {@code closable}. */
    public record ProfessionalAppointment(AppointmentView appointment, ProfessionalPatientRef patient,
            boolean closable) {
    }

    static final int MAX_RANGE_DAYS = 62;
    static final String NOT_FOUND = "La cita no existe";

    private final ProfessionalRepository professionals;
    private final AppointmentRepository appointments;
    private final AppointmentQueries queries;
    private final TransactionRunner tx;
    private final Clock clock;

    public ProfessionalAppointmentsUseCase(ProfessionalRepository professionals, AppointmentRepository appointments,
            AppointmentQueries queries, TransactionRunner tx, Clock clock) {
        this.professionals = professionals;
        this.appointments = appointments;
        this.queries = queries;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * HU-020: citas {@code APPROVED} propias entre {@code from} y {@code to} (dia: from = to; semana:
     * la calcula el cliente, D35), opcionalmente de una sede. Mismo limite de rango que el calendario
     * de bloques (62 dias).
     */
    public List<ProfessionalAppointment> agenda(long userId, LocalDate from, LocalDate to, Integer siteId) {
        if (from == null || to == null || to.isBefore(from)) {
            throw InvalidRequestException.field("to", "Indique un rango de fechas válido (desde ≤ hasta)");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw InvalidRequestException.field("to", "El rango máximo es de " + MAX_RANGE_DAYS + " días");
        }
        LocalDateTime now = SystemZone.now(clock);
        return queries.findApprovedByProfessional(professionalOf(userId).id(), from, to, siteId).stream()
                .map(v -> present(v, now)).toList();
    }

    /** HU-021 CA-01: la atencion se realizo. Historial PROFESSIONAL con el usuario del profesional. */
    public ProfessionalAppointment complete(long userId, long appointmentId) {
        return close(userId, appointmentId, (appointment, now) -> appointment.complete(userId, now));
    }

    /** HU-021 CA-02: el paciente no asistio. Historial PROFESSIONAL con el usuario del profesional. */
    public ProfessionalAppointment noShow(long userId, long appointmentId) {
        return close(userId, appointmentId, (appointment, now) -> appointment.noShow(userId, now));
    }

    /**
     * Una sola transaccion (CA-08): se bloquea la fila de la cita, se comprueba el titular, el dominio
     * decide la transicion y el adaptador guarda estado e historial juntos. Si algo falla, no queda
     * nada. Las reservas NO se liberan: {@code COMPLETED} y {@code NO_SHOW} no liberan franjas.
     */
    private ProfessionalAppointment close(long userId, long appointmentId,
            BiFunction<Appointment, LocalDateTime, Appointment.Transition> closing) {
        tx.inTransaction(() -> {
            Professional professional = professionalOf(userId);
            Appointment appointment = Ownership.requireOwned(appointments.lockById(appointmentId),
                    Appointment::professionalId, professional.id(), NOT_FOUND);
            appointments.apply(closing.apply(appointment, SystemZone.now(clock)));
            return null;
        });
        AppointmentView view = queries.findById(appointmentId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return present(view, SystemZone.now(clock));
    }

    private Professional professionalOf(long userId) {
        return professionals.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No hay un perfil profesional para esta cuenta"));
    }

    private static ProfessionalAppointment present(AppointmentView view, LocalDateTime now) {
        return new ProfessionalAppointment(view,
                new ProfessionalPatientRef(view.patient().fullName(), view.patient().documentType(),
                        view.patient().documentNumber()),
                PatientAppointmentsUseCase.toDomain(view).isClosableAt(now));
    }
}
