package com.fcv.citas.domain.appointment;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.IntFunction;

import com.fcv.citas.domain.shared.BusinessRuleException;
import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Solicitud de reprogramacion de una cita (RF-15, HU-027, HU-031). Agregado propio: la cita original
 * NO cambia mientras la solicitud esta {@code PENDING} (RN-10); solo la aprobacion la mueve.
 *
 * <p>Invariantes: nace {@code PENDING} sobre una cita {@code APPROVED} que aun no empezo y sin otra
 * solicitud sin decidir (D20); conserva profesional y especialidad (la franja nueva solo lleva dia,
 * horas y sede, D21); la franja propuesta es futura, distinta de la actual y no se cruza con ella. Solo
 * una {@code PENDING} se decide, y solo sobre una cita todavia {@code APPROVED} (CA-05 de HU-031).</p>
 *
 * <p>La ocupacion de slots (retener, convertir, liberar) no vive aqui sino en el libro unico
 * {@code slot_reservations} (dec-003); el caso de uso la ordena en la misma transaccion.</p>
 */
public record RescheduleRequest(
        Long id,
        long appointmentId,
        RescheduleStatus status,
        long requestedByUserId,
        /** Franja de la cita al pedirla (V10); tras aprobar ya no esta en {@code appointments}. */
        TimeSlot previous,
        TimeSlot proposed,
        String requestReason,
        Long decidedByUserId,
        String decisionReason) {

    public static final int MAX_REASON = StatusChange.MAX_REASON;

    /** D37: motivo automatico cuando la solicitud se cierra porque el paciente cancela la cita (D18). */
    public static final String CANCELLED_WITH_APPOINTMENT = "Cita cancelada por el paciente";

    /** Resultado de aprobar: la solicitud decidida y la cita movida con su fila de historial (D22). */
    public record Approval(RescheduleRequest request, Appointment.Transition appointment) {
    }

    /** Resultado de rechazar: la solicitud decidida y la fila de historial de la cita, que no cambia (D22). */
    public record Rejection(RescheduleRequest request, Appointment.Transition appointment) {
    }

    public RescheduleRequest {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(proposed, "proposed");
        if (status == RescheduleStatus.PENDING && decidedByUserId != null) {
            throw new IllegalArgumentException("Una solicitud pendiente no tiene decisor");
        }
        if (status != RescheduleStatus.PENDING && decidedByUserId == null) {
            throw new IllegalArgumentException("Una solicitud decidida exige decisor");
        }
    }

    // ------------------------------------------------------------------ HU-027: pedir

    /**
     * Estado de la cita que admite pedir (409): {@code APPROVED} ({@code INVALID_TRANSITION}), que no
     * haya empezado ({@code APPOINTMENT_EXPIRED}) y sin otra solicitud sin decidir
     * ({@code RESCHEDULE_PENDING}, D20). Es la misma regla que {@link Appointment#isReschedulableAt}.
     */
    public static void requireRequestable(Appointment appointment, LocalDateTime now, boolean hasPending) {
        if (appointment.status() != AppointmentStatus.APPROVED) {
            throw new ConflictException("INVALID_TRANSITION",
                    "Solo se puede reprogramar una cita aprobada (está en " + appointment.status() + ")");
        }
        if (appointment.hasStartedAt(now)) {
            throw new ConflictException("APPOINTMENT_EXPIRED",
                    "La cita ya empezó o ya pasó: no se puede reprogramar");
        }
        if (hasPending) {
            throw new ConflictException("RESCHEDULE_PENDING",
                    "Esta cita ya tiene una reprogramación pendiente de decisión");
        }
    }

    /**
     * CA-02 de HU-027 (RF-15): la reprogramacion conserva profesional y especialidad. Si el cliente
     * envia otros, no es una reprogramacion sino una cita nueva ({@code WRONG_FLOW}, 422).
     */
    public static void requireSameCare(Appointment appointment, Long professionalId, Integer specialtyId) {
        if ((professionalId != null && professionalId != appointment.professionalId())
                || (specialtyId != null && specialtyId != appointment.specialtyId())) {
            throw new BusinessRuleException("WRONG_FLOW",
                    "Cambiar de profesional o de especialidad es una cita nueva: búsquela y agéndela desde la"
                            + " disponibilidad");
        }
    }

    /**
     * HU-027: crea la solicitud {@code PENDING}. La franja anterior es la actual de la cita; la cita no
     * se toca (RN-10). {@code proposed} ya trae la hora de fin segun la duracion de la especialidad.
     */
    public static RescheduleRequest request(Appointment appointment, long requesterUserId, TimeSlot proposed,
            String reason, LocalDateTime now, boolean hasPending) {
        Objects.requireNonNull(appointment.id(), "appointment.id");
        if (appointment.patientUserId() != requesterUserId) {
            throw new IllegalArgumentException("Solo el titular pide reprogramar su cita");
        }
        requireRequestable(appointment, now, hasPending);
        TimeSlot current = appointment.slot();
        if (proposed.isSameAs(current)) {
            throw new BusinessRuleException("SAME_SLOT", "La franja propuesta es la misma que la actual");
        }
        if (proposed.hasStartedAt(now)) {
            throw new BusinessRuleException("PAST_TIME", "No se pueden reservar franjas en el pasado");
        }
        if (proposed.overlaps(current)) {
            throw new BusinessRuleException("SLOT_NOT_AVAILABLE",
                    "La franja propuesta se cruza con la actual de la cita: elija una que no la toque");
        }
        return new RescheduleRequest(null, appointment.id(), RescheduleStatus.PENDING, requesterUserId, current,
                proposed, normalizeReason(reason), null, null);
    }

    // ------------------------------------------------------------------ HU-031: decidir

    /**
     * HU-031 CA-01: aprueba y mueve la MISMA cita a la franja propuesta (su id se conserva). 409
     * {@code INVALID_TRANSITION} si la solicitud ya no esta {@code PENDING} o la cita ya no esta
     * {@code APPROVED}; 409 {@code APPOINTMENT_EXPIRED} si la franja propuesta ya empezo (D23, CA-09).
     * El historial queda con estado {@code APPROVED}, origen ADMIN y un motivo que nombra las dos
     * franjas (D22).
     */
    public Approval approve(long adminUserId, Appointment appointment, LocalDateTime now,
            IntFunction<String> siteLabel) {
        requireDecidable(appointment);
        if (proposed.hasStartedAt(now)) {
            throw new ConflictException("APPOINTMENT_EXPIRED",
                    "La franja propuesta ya pasó: rechace la solicitud indicando el motivo");
        }
        String trace = "Reprogramación aprobada: de " + previous.describe(siteLabel) + " a "
                + proposed.describe(siteLabel);
        return new Approval(decide(RescheduleStatus.APPROVED, adminUserId, null),
                appointment.rescheduleTo(proposed, adminUserId, trace));
    }

    /**
     * HU-031 CA-03 y CA-04 (RN-04): rechaza con motivo obligatorio; la cita queda intacta. Primero el
     * estado (409) y despues el motivo (400), igual que {@link Appointment#reject}.
     */
    public Rejection reject(long adminUserId, Appointment appointment, String reason) {
        requireDecidable(appointment);
        String normalized = normalizeReason(reason);
        if (normalized == null) {
            throw InvalidRequestException.field("reason", "El motivo del rechazo es obligatorio");
        }
        return new Rejection(decide(RescheduleStatus.REJECTED, adminUserId, normalized),
                appointment.recordRescheduleRejection(adminUserId, normalized));
    }

    /**
     * D18 / D37: el paciente cancela la cita con esta solicitud sin decidir. Queda {@code CANCELLED},
     * con el paciente como decisor y un motivo automatico. Liberar su retencion lo hace el caso de uso
     * con el unico camino de liberacion ({@code ReservationHolder#ofAppointment}).
     */
    public RescheduleRequest cancelWithAppointment(long patientUserId) {
        requirePending();
        return decide(RescheduleStatus.CANCELLED, patientUserId, CANCELLED_WITH_APPOINTMENT);
    }

    public boolean isPending() {
        return status == RescheduleStatus.PENDING;
    }

    private void requireDecidable(Appointment appointment) {
        if (appointment.id() == null || appointment.id() != appointmentId) {
            throw new IllegalArgumentException("La cita no corresponde a esta solicitud");
        }
        requirePending();
        if (appointment.status() != AppointmentStatus.APPROVED) {
            throw new ConflictException("INVALID_TRANSITION",
                    "La cita ya no está aprobada (está en " + appointment.status() + "): la solicitud no se"
                            + " puede decidir");
        }
    }

    private void requirePending() {
        if (!isPending()) {
            throw new ConflictException("INVALID_TRANSITION",
                    "La solicitud de reprogramación ya fue decidida (está en " + status + ")");
        }
    }

    private RescheduleRequest decide(RescheduleStatus next, long deciderUserId, String reason) {
        return new RescheduleRequest(id, appointmentId, next, requestedByUserId, previous, proposed, requestReason,
                deciderUserId, reason);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON) {
            throw InvalidRequestException.field("reason", "El motivo admite como máximo 500 caracteres");
        }
        return trimmed;
    }
}
