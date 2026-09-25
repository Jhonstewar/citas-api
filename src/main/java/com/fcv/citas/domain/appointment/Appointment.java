package com.fcv.citas.domain.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Cita medica (RF-11 a RF-13). Nace aprobada si es general (RN-02) o solicitada si es
 * especializada (RN-03); toda transicion pasa por {@link #transitionTo} y deja un
 * {@link StatusChange} que se persiste en la misma transaccion (HU-032 CA-05).
 */
public record Appointment(
        Long id,
        long patientUserId,
        long professionalId,
        int siteId,
        int specialtyId,
        AppointmentStatus status,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime) {

    /** Resultado de una transicion: la cita con su nuevo estado y la fila de historial. */
    public record Transition(Appointment appointment, StatusChange change) {
    }

    public Appointment {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("La cita debe terminar despues de empezar");
        }
    }

    /** RN-02 / HU-023: cita general, aprobada sin intervencion del ADMIN; historial SYSTEM sin actor. */
    public static Transition bookGeneral(long patientUserId, long professionalId, int siteId, int specialtyId,
            LocalDate date, LocalTime start, LocalTime end) {
        Appointment a = new Appointment(null, patientUserId, professionalId, siteId, specialtyId,
                AppointmentStatus.APPROVED, date, start, end);
        return new Transition(a, new StatusChange(AppointmentStatus.APPROVED, null, AuditSource.SYSTEM, null));
    }

    /** RN-03 / HU-024: solicitud especializada en REQUESTED; historial USER con el paciente como actor. */
    public static Transition requestSpecialized(long patientUserId, long professionalId, int siteId,
            int specialtyId, LocalDate date, LocalTime start, LocalTime end) {
        Appointment a = new Appointment(null, patientUserId, professionalId, siteId, specialtyId,
                AppointmentStatus.REQUESTED, date, start, end);
        return new Transition(a, new StatusChange(AppointmentStatus.REQUESTED, patientUserId, AuditSource.USER, null));
    }

    /** HU-030 CA-01 y D12: solo una REQUESTED que aun no empezo. */
    public Transition approve(long adminUserId, LocalDateTime now) {
        requireRequested();
        if (hasStartedAt(now)) {
            throw new ConflictException("APPOINTMENT_EXPIRED",
                    "La franja de esta solicitud ya pasó: recházela indicando el motivo");
        }
        return transitionTo(AppointmentStatus.APPROVED, adminUserId, AuditSource.ADMIN, null);
    }

    // ------------------------------------------------------------------ S4: cancelar (HU-026)

    /**
     * HU-026 / RF-14: el paciente cancela una cita futura no terminal. {@code REQUESTED} y
     * {@code APPROVED} se cancelan (D16) y no hay antelacion minima: basta con que no haya empezado
     * (D17). Primero el estado (409 {@code INVALID_TRANSITION}) y despues la hora (409
     * {@code APPOINTMENT_EXPIRED}), igual que en {@link #reject}. El motivo es opcional (≤ 500).
     * Liberar las reservas es consecuencia de {@link AppointmentStatus#releasesSlots()} y la hace el
     * caso de uso en la misma transaccion (CA-08).
     */
    public Transition cancel(long patientUserId, LocalDateTime now, String reason) {
        if (!status.canTransitionTo(AppointmentStatus.CANCELLED)) {
            throw new ConflictException("INVALID_TRANSITION",
                    "La cita está en estado " + status + " y ya no se puede cancelar");
        }
        if (hasStartedAt(now)) {
            throw new ConflictException("APPOINTMENT_EXPIRED",
                    "La cita ya empezó o ya pasó: no se puede cancelar");
        }
        return transitionTo(AppointmentStatus.CANCELLED, patientUserId, AuditSource.USER, reason);
    }

    /** Lo que {@link #cancel} aceptaria en este instante ({@code cancellable} del contrato S4). */
    public boolean isCancellableAt(LocalDateTime now) {
        return status.canTransitionTo(AppointmentStatus.CANCELLED) && !hasStartedAt(now);
    }

    /**
     * D20 / HU-027: se puede pedir reprogramacion de una {@code APPROVED} futura sin otra solicitud
     * {@code PENDING} ({@code reschedulable} del contrato S4). Coincide con lo que exige
     * {@link RescheduleRequest#requireRequestable}.
     */
    public boolean isReschedulableAt(LocalDateTime now, boolean hasPendingReschedule) {
        return status == AppointmentStatus.APPROVED && !hasStartedAt(now) && !hasPendingReschedule;
    }

    // ------------------------------------------------------------------ S4: reprogramacion (HU-031)

    /** La franja que ocupa hoy la cita (dia, horas y sede). */
    public TimeSlot slot() {
        return new TimeSlot(date, startTime, endTime, siteId);
    }

    /**
     * HU-031 CA-01: la reprogramacion aprobada mueve ESTA cita (mismo id, profesional y especialidad) a
     * la franja nueva, incluida la sede (D21). El estado sigue {@code APPROVED}, pero queda una fila de
     * historial {@code APPROVED}/ADMIN con el motivo que nombra las dos franjas (D22). No pasa por
     * {@link #transitionTo}: no es un cambio de estado. La llama {@link RescheduleRequest#approve}.
     */
    Transition rescheduleTo(TimeSlot target, long adminUserId, String trace) {
        requireApprovedForReschedule();
        Appointment moved = new Appointment(id, patientUserId, professionalId, target.siteId(), specialtyId, status,
                target.date(), target.startTime(), target.endTime());
        return new Transition(moved, new StatusChange(AppointmentStatus.APPROVED, adminUserId, AuditSource.ADMIN,
                trace));
    }

    /**
     * HU-031 CA-06 (D22): el rechazo de una reprogramacion deja la cita intacta y una fila de historial
     * {@code APPROVED}/ADMIN con el motivo enviado. La llama {@link RescheduleRequest#reject}.
     */
    Transition recordRescheduleRejection(long adminUserId, String reason) {
        requireApprovedForReschedule();
        return new Transition(this, new StatusChange(AppointmentStatus.APPROVED, adminUserId, AuditSource.ADMIN,
                reason));
    }

    private void requireApprovedForReschedule() {
        if (status != AppointmentStatus.APPROVED) {
            throw new ConflictException("INVALID_TRANSITION",
                    "La cita ya no está aprobada (está en " + status + ")");
        }
    }

    // ------------------------------------------------------------------ S4: cierre de atencion (HU-021)

    /** HU-021: el profesional marca la atencion como realizada. Historial PROFESSIONAL con actor. */
    public Transition complete(long professionalUserId, LocalDateTime now) {
        return close(AppointmentStatus.COMPLETED, professionalUserId, now);
    }

    /** HU-021: el profesional marca la inasistencia del paciente. Historial PROFESSIONAL con actor. */
    public Transition noShow(long professionalUserId, LocalDateTime now) {
        return close(AppointmentStatus.NO_SHOW, professionalUserId, now);
    }

    /**
     * D19 — UNICO punto de decision de "cuando se puede cerrar": una {@code APPROVED} desde su hora
     * de inicio, sin plazo maximo. Cambiar la politica (p. ej. esperar al final de la franja) es
     * cambiar solo este metodo (T-02 de HU-021). Es tambien el {@code closable} del contrato S4.
     */
    public boolean isClosableAt(LocalDateTime now) {
        return status == AppointmentStatus.APPROVED && hasStartedAt(now);
    }

    private Transition close(AppointmentStatus target, long professionalUserId, LocalDateTime now) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("INVALID_TRANSITION",
                    "La cita está en estado " + status + " y no puede pasar a " + target);
        }
        // El estado ya es valido; si aun asi no es cerrable, lo que falta es que empiece.
        if (!isClosableAt(now)) {
            throw new ConflictException("APPOINTMENT_NOT_STARTED",
                    "La cita aún no ha empezado: se puede cerrar desde su hora de inicio");
        }
        return transitionTo(target, professionalUserId, AuditSource.PROFESSIONAL, null);
    }

    /**
     * "Ya empezo" en la zona del sistema (el {@code now} lo da {@code SystemZone.now}). Unica
     * comparacion temporal de la cita: cancelar, aprobar, reprogramar y cerrar la comparten.
     */
    public boolean hasStartedAt(LocalDateTime now) {
        return !startsAt().isAfter(now);
    }

    /** HU-030 CA-02 y CA-03 (RN-04): rechazo con motivo obligatorio. */
    public Transition reject(long adminUserId, String reason) {
        // Primero el estado: sobre una cita ya decidida el problema es la transicion (409), no el motivo.
        requireRequested();
        if (reason == null || reason.isBlank()) {
            throw InvalidRequestException.field("reason", "El motivo del rechazo es obligatorio");
        }
        return transitionTo(AppointmentStatus.REJECTED, adminUserId, AuditSource.ADMIN, reason);
    }

    /** RN-11: unica puerta de cambio de estado. Transicion no permitida → 409. */
    public Transition transitionTo(AppointmentStatus next, Long actorUserId, AuditSource source, String reason) {
        if (!status.canTransitionTo(next)) {
            throw new ConflictException("INVALID_TRANSITION",
                    "La cita está en estado " + status + " y no puede pasar a " + next);
        }
        Appointment updated = new Appointment(id, patientUserId, professionalId, siteId, specialtyId, next, date,
                startTime, endTime);
        return new Transition(updated, new StatusChange(next, actorUserId, source, reason));
    }

    public LocalDateTime startsAt() {
        return LocalDateTime.of(date, startTime);
    }

    private void requireRequested() {
        if (status != AppointmentStatus.REQUESTED) {
            throw new ConflictException("INVALID_TRANSITION",
                    "La cita no está en estado REQUESTED (está en " + status + ")");
        }
    }
}
