package com.fcv.citas.domain.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Bloque de disponibilidad de un profesional en una sede y un dia (RF-08). Se discretiza en slots
 * de 30 minutos; las horas caen en la rejilla de media hora (:00 o :30).
 */
public record AvailabilityBlock(
        Long id,
        long professionalId,
        int siteId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime) {

    public static final int SLOT_MINUTES = 30;

    public AvailabilityBlock {
        Objects.requireNonNull(date, "date");
        if (startTime == null) {
            throw InvalidRequestException.field("startTime", "La hora de inicio es obligatoria");
        }
        if (endTime == null) {
            throw InvalidRequestException.field("endTime", "La hora de fin es obligatoria");
        }
        requireGrid(startTime, "startTime");
        requireGrid(endTime, "endTime");
        if (!endTime.isAfter(startTime)) {
            throw InvalidRequestException.field("endTime", "La hora de fin debe ser posterior a la de inicio");
        }
    }

    private static void requireGrid(LocalTime time, String field) {
        if (time.getMinute() % SLOT_MINUTES != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw InvalidRequestException.field(field, "Las horas van en intervalos de 30 minutos (:00 o :30)");
        }
    }

    /** Inicios de los slots de 30 minutos, en orden (HU-017 CA-02). */
    public List<LocalTime> slotStarts() {
        List<LocalTime> starts = new ArrayList<>();
        for (LocalTime t = startTime; t.isBefore(endTime); t = t.plusMinutes(SLOT_MINUTES)) {
            starts.add(t);
        }
        return starts;
    }

    /** Mismo dia y rangos que se cruzan; bloques contiguos (12:00 fin, 12:00 inicio) no solapan. */
    public boolean overlaps(AvailabilityBlock other) {
        return date.equals(other.date) && startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }

    /** RN-06: el bloque ya empezo (o termino) en el instante dado, hora local del sistema. */
    public boolean hasStartedAt(LocalDateTime now) {
        return !LocalDateTime.of(date, startTime).isAfter(now);
    }

    /**
     * Cierto si una cita que empieza en {@code start} y ocupa {@code slots} slots consecutivos cabe
     * entera en este bloque (RN-05; D9: los 2 slots de 60 min son del mismo bloque).
     */
    public boolean canHost(LocalTime start, int slots) {
        if (start.getMinute() % SLOT_MINUTES != 0 || start.getSecond() != 0 || start.isBefore(startTime)) {
            return false;
        }
        LocalTime end = start.plusMinutes((long) SLOT_MINUTES * slots);
        return !end.isAfter(endTime) && end.isAfter(start);
    }
}
