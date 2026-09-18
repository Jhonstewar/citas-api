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
        if (!startsAt().isAfter(now)) {
            throw new ConflictException("APPOINTMENT_EXPIRED",
                    "La franja de esta solicitud ya pasó: recházela indicando el motivo");
        }
        return transitionTo(AppointmentStatus.APPROVED, adminUserId, AuditSource.ADMIN, null);
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
