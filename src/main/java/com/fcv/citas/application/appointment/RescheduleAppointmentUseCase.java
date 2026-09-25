package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.function.IntFunction;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.appointment.AppointmentQueries.RescheduleView;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.Detail;
import com.fcv.citas.application.shared.Ownership;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.appointment.RescheduleRequest;
import com.fcv.citas.domain.appointment.RescheduleRequestRepository;
import com.fcv.citas.domain.appointment.ReservationHolder;
import com.fcv.citas.domain.appointment.TimeSlot;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.schedule.BlockRepository;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * Reprogramacion de una cita (RF-15): el paciente la pide (HU-027) y el ADMIN la aprueba o la rechaza
 * (HU-031). Las reglas viven en el agregado {@link RescheduleRequest} y en {@link Appointment}; aqui
 * solo se ordenan los pasos dentro de UNA transaccion y el orden de bloqueo.
 *
 * <p><b>Orden de bloqueo</b> (el mismo que la cancelacion, HU-026): primero la fila de la CITA y
 * despues la de la solicitud. Asi pedir, decidir y cancelar sobre la misma cita se serializan sin
 * interbloquearse, y la segunda operacion ve el estado que dejo la primera (HU-031 CA-07).</p>
 *
 * <p><b>Ocupacion de slots</b> (libro unico, dec-003): pedir RETIENE la franja nueva con la misma
 * barrera de PK que la reserva; aprobar libera la franja antigua y CONVIERTE la retencion en
 * ocupacion de la cita actualizando las filas (sin borrar y reinsertar, RN-01); rechazar libera la
 * retencion por el unico camino de liberacion.</p>
 */
public class RescheduleAppointmentUseCase {

    /**
     * Cuerpo de la solicitud. {@code professionalId} y {@code specialtyId} son opcionales y solo sirven
     * para rechazar un cambio de profesional o de especialidad (CA-02 de HU-027): la reprogramacion
     * siempre conserva los de la cita.
     */
    public record RescheduleCommand(Integer siteId, LocalDate date, LocalTime startTime, String reason,
            Long professionalId, Integer specialtyId) {
    }

    static final String REQUEST_NOT_FOUND = "La solicitud de reprogramación no existe";

    private final SlotAllocator allocator;
    private final AppointmentRepository appointments;
    private final RescheduleRequestRepository reschedules;
    private final AppointmentQueries queries;
    private final AdminAppointmentsUseCase admin;
    private final TransactionRunner tx;
    private final Clock clock;

    public RescheduleAppointmentUseCase(SpecialtyRepository specialties, ProfessionalRepository professionals,
            BlockRepository blocks, AppointmentRepository appointments, RescheduleRequestRepository reschedules,
            AppointmentQueries queries, AdminAppointmentsUseCase admin, TransactionRunner tx, Clock clock) {
        this.allocator = new SlotAllocator(specialties, professionals, blocks, clock);
        this.appointments = appointments;
        this.reschedules = reschedules;
        this.queries = queries;
        this.admin = admin;
        this.tx = tx;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ HU-027

    /**
     * HU-027: crea la solicitud {@code PENDING} y retiene la franja nueva; la cita no cambia (RN-10).
     * Errores en este orden: 400 campos; 404 cita ajena o inexistente; 409 {@code INVALID_TRANSITION} /
     * {@code APPOINTMENT_EXPIRED} / {@code RESCHEDULE_PENDING}; 422 {@code WRONG_FLOW} (otro profesional o
     * especialidad); las reglas de franja de la reserva ({@code SPECIALTY_INACTIVE},
     * {@code PROFESSIONAL_INACTIVE}, {@code SPECIALTY_NOT_ASSIGNED}, {@code SITE_NOT_ASSIGNED},
     * {@code PAST_TIME}, {@code SLOT_NOT_AVAILABLE}); 422 {@code SAME_SLOT}; 409 {@code SLOT_TAKEN}.
     */
    public RescheduleView request(long patientUserId, long appointmentId, RescheduleCommand c) {
        requireFields(c);
        long requestId = tx.inTransaction(() -> {
            // 1. Ownership: ajena = inexistente (404). Bloquea la cita: serializa con cancelar y decidir.
            Appointment appointment = Ownership.requireOwned(appointments.lockById(appointmentId),
                    Appointment::patientUserId, patientUserId, PatientAppointmentsUseCase.NOT_FOUND);
            LocalDateTime now = SystemZone.now(clock);
            boolean pending = reschedules.lockPendingByAppointment(appointmentId).isPresent();
            // 2. Estado de la cita (409) y conservacion de profesional y especialidad (422).
            RescheduleRequest.requireRequestable(appointment, now, pending);
            RescheduleRequest.requireSameCare(appointment, c.professionalId(), c.specialtyId());
            // 3. La franja nueva, con las MISMAS reglas que la reserva (RN-05, RN-06, RN-07, RN-08).
            SlotAllocator.Allocation slot = allocator.allocate(appointment.professionalId(), c.siteId(),
                    appointment.specialtyId(), c.date(), c.startTime(), null);
            TimeSlot proposed = new TimeSlot(c.date(), c.startTime(), slot.end(), c.siteId());
            // 4. El agregado: PENDING con la franja anterior y la propuesta (SAME_SLOT, cruce, pasado).
            RescheduleRequest created = reschedules.create(
                    RescheduleRequest.request(appointment, patientUserId, proposed, c.reason(), now, pending));
            // 5. Retencion con la barrera de PK: si otra reserva gano la franja, 409 y rollback total.
            appointments.holdForReschedule(created.id(), slot.slotIds());
            return created.id();
        });
        return queries.findReschedule(requestId).orElseThrow();
    }

    // ------------------------------------------------------------------ HU-031

    /**
     * HU-031 CA-01/CA-02: aprueba y mueve la cita (mismo id). Libera la franja antigua y convierte la
     * retencion en ocupacion de la cita sin abrir hueco. 404 · 409 {@code INVALID_TRANSITION} /
     * {@code APPOINTMENT_EXPIRED} (D23).
     */
    public Detail approve(long adminUserId, long requestId) {
        long appointmentId = tx.inTransaction(() -> {
            Locked locked = lock(requestId);
            RescheduleRequest.Approval approval = locked.request().approve(adminUserId, locked.appointment(),
                    SystemZone.now(clock), locked.siteLabel());
            long id = locked.appointment().id();
            appointments.releaseReservations(ReservationHolder.ofCurrentSlot(id));
            appointments.convertHeldToAppointment(requestId, id);
            appointments.apply(approval.appointment());
            reschedules.saveDecision(approval.request());
            return id;
        });
        return admin.detail(appointmentId);
    }

    /**
     * HU-031 CA-03/CA-04: rechaza con motivo obligatorio; la cita queda intacta y la retencion se libera
     * (RN-09, RN-10). 404 · 409 {@code INVALID_TRANSITION} · 400 sin motivo.
     */
    public Detail reject(long adminUserId, long requestId, String reason) {
        long appointmentId = tx.inTransaction(() -> {
            Locked locked = lock(requestId);
            RescheduleRequest.Rejection rejection = locked.request().reject(adminUserId, locked.appointment(), reason);
            appointments.releaseReservations(ReservationHolder.ofRescheduleRequest(requestId));
            appointments.apply(rejection.appointment());
            reschedules.saveDecision(rejection.request());
            return locked.appointment().id();
        });
        return admin.detail(appointmentId);
    }

    private record Locked(RescheduleRequest request, Appointment appointment, IntFunction<String> siteLabel) {
    }

    /** Cita primero, solicitud despues (orden unico de bloqueo); la solicitud se relee ya bloqueada. */
    private Locked lock(long requestId) {
        RescheduleView view = queries.findReschedule(requestId)
                .orElseThrow(() -> new NotFoundException(REQUEST_NOT_FOUND));
        Appointment appointment = appointments.lockById(view.appointmentId())
                .orElseThrow(() -> new NotFoundException(REQUEST_NOT_FOUND));
        RescheduleRequest request = reschedules.lockById(requestId)
                .orElseThrow(() -> new NotFoundException(REQUEST_NOT_FOUND));
        // Las dos franjas pueden estar en la misma sede: Map.of rechazaria la clave repetida.
        Map<Integer, String> codes = new java.util.HashMap<>();
        codes.put(view.previous().site().id(), view.previous().site().code());
        codes.put(view.proposed().site().id(), view.proposed().site().code());
        return new Locked(request, appointment, id -> codes.getOrDefault(id, String.valueOf(id)));
    }

    private static void requireFields(RescheduleCommand c) {
        if (c.siteId() == null) {
            throw InvalidRequestException.field("siteId", "Seleccione la sede");
        }
        if (c.date() == null) {
            throw InvalidRequestException.field("date", "Seleccione la fecha");
        }
        if (c.startTime() == null) {
            throw InvalidRequestException.field("startTime", "Seleccione la hora");
        }
    }
}
