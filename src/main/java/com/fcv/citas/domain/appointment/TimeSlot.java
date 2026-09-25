package com.fcv.citas.domain.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Franja concreta de una cita: dia, horas y sede ({@code TimeSlot} del contrato S4). La sede forma
 * parte de la franja porque una reprogramacion puede cambiarla (D21).
 */
public record TimeSlot(LocalDate date, LocalTime startTime, LocalTime endTime, int siteId) {

    public TimeSlot {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("La franja debe terminar despues de empezar");
        }
    }

    public LocalDateTime startsAt() {
        return LocalDateTime.of(date, startTime);
    }

    /** Misma regla de "ya empezo" que {@link Appointment#hasStartedAt}: el inicio no es posterior a ahora. */
    public boolean hasStartedAt(LocalDateTime now) {
        return !startsAt().isAfter(now);
    }

    /** Misma franja: mismo dia, misma hora de inicio y misma sede ({@code SAME_SLOT}). */
    public boolean isSameAs(TimeSlot other) {
        return date.equals(other.date) && startTime.equals(other.startTime) && siteId == other.siteId;
    }

    /**
     * Se cruzan en el tiempo el mismo dia. No mira la sede: un profesional no atiende en dos sitios a la
     * vez, asi que dos franjas suyas que se cruzan chocan aunque las sedes difieran.
     */
    public boolean overlaps(TimeSlot other) {
        return date.equals(other.date) && startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }

    /** Texto legible para el historial (D22): {@code 2026-10-01 08:00–09:00 (HIC)}. */
    public String describe(IntFunction<String> siteLabel) {
        return date + " " + startTime + "–" + endTime + " (" + siteLabel.apply(siteId) + ")";
    }
}
