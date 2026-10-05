package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;

/**
 * HU-035: hecho ocurrido sobre una cita, ya confirmado por la transaccion. Java puro: no sabe de
 * JSON ni de HTTP; el adaptador decide como serializarlo.
 *
 * @param eventId       identificador unico del evento (UUID v4), clave de idempotencia para el destino
 * @param type          clase de transicion (D-E)
 * @param appointmentId cita afectada
 * @param occurredAt    instante de la publicacion, con el desfase de la zona del sistema
 * @param appointment   vista de la cita leida tras el commit
 * @param reason        texto libre opcional (rechazo o cancelacion); contenido NO confiable; puede ser nulo
 */
public record AppointmentEvent(UUID eventId, AppointmentEventType type, long appointmentId,
        OffsetDateTime occurredAt, AppointmentView appointment, String reason) {

    public AppointmentEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(appointment, "appointment");
    }

    /** Evento nuevo: {@code eventId} aleatorio (v4) e instante tomado del reloj inyectado. */
    public static AppointmentEvent of(AppointmentEventType type, AppointmentView appointment, String reason,
            Clock clock) {
        String normalized = reason == null || reason.isBlank() ? null : reason;
        return new AppointmentEvent(UUID.randomUUID(), type, appointment.id(), OffsetDateTime.now(clock),
                appointment, normalized);
    }
}
