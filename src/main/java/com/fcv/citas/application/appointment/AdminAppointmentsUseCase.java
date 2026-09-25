package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.appointment.AppointmentQueries.InboxFilter;
import com.fcv.citas.application.appointment.AppointmentQueries.RescheduleView;
import com.fcv.citas.application.appointment.AppointmentQueries.Summary;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.Detail;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.appointment.ReservationHolder;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-029 (bandeja) y HU-030 (aprobar o rechazar una cita especializada). Cada decision bloquea la
 * fila de la cita: dos decisiones simultaneas se serializan y la segunda ve el estado ya cambiado
 * (409 INVALID_TRANSITION, CA-07). Estado, historial y liberacion de slots van en la misma transaccion.
 * Las decisiones sobre reprogramaciones (HU-031) estan en {@link RescheduleAppointmentUseCase}.
 */
public class AdminAppointmentsUseCase {

    /** Clase de entrada de la bandeja (contrato S4, {@code InboxEntry}). */
    public enum InboxType {
        APPOINTMENT_REQUEST,
        RESCHEDULE_REQUEST
    }

    /**
     * Una entrada de la bandeja. {@code reschedule} solo existe en las {@code RESCHEDULE_REQUEST}: la
     * cita trae la franja actual y la solicitud la anterior y la propuesta (HU-029 CA-04).
     */
    public record InboxItem(InboxType type, AppointmentView appointment, RescheduleView reschedule) {

        /** Franja por la que se ordena: la propuesta en una reprogramacion (D24), la de la cita si no. */
        LocalDate date() {
            return reschedule == null ? appointment.date() : reschedule.proposed().date();
        }

        LocalTime startTime() {
            return reschedule == null ? appointment.startTime() : reschedule.proposed().startTime();
        }
    }

    private static final Comparator<InboxItem> INBOX_ORDER = Comparator.comparing(InboxItem::date)
            .thenComparing(InboxItem::startTime).thenComparing(InboxItem::type)
            .thenComparingLong(i -> i.reschedule() == null ? i.appointment().id() : i.reschedule().id());

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

    /**
     * HU-029: especializadas {@code REQUESTED} y reprogramaciones {@code PENDING}, en un orden estable
     * por franja. {@code type} opcional filtra la clase de entrada; un valor desconocido → 400.
     */
    public List<InboxItem> inbox(InboxFilter filter, String type) {
        InboxType only = parseType(type);
        List<InboxItem> items = new ArrayList<>();
        if (only == null || only == InboxType.APPOINTMENT_REQUEST) {
            queries.findPendingRequests(filter)
                    .forEach(v -> items.add(new InboxItem(InboxType.APPOINTMENT_REQUEST, v, null)));
        }
        if (only == null || only == InboxType.RESCHEDULE_REQUEST) {
            queries.findPendingReschedules(filter).forEach(p -> items.add(
                    new InboxItem(InboxType.RESCHEDULE_REQUEST, p.appointment(), p.reschedule())));
        }
        items.sort(INBOX_ORDER);
        return items;
    }

    public Detail detail(long appointmentId) {
        AppointmentView view = queries.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("La cita no existe"));
        return new Detail(view, queries.history(appointmentId), queries.lastReschedule(appointmentId).orElse(null));
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
                appointments.releaseReservations(ReservationHolder.ofAppointment(appointmentId));
            }
            return null;
        });
        return detail(appointmentId);
    }

    private Appointment locked(long appointmentId) {
        return appointments.lockById(appointmentId).orElseThrow(() -> new NotFoundException("La cita no existe"));
    }

    private static InboxType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return InboxType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw InvalidRequestException.field("type",
                    "El tipo debe ser APPOINTMENT_REQUEST o RESCHEDULE_REQUEST");
        }
    }
}
