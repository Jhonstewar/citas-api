package com.fcv.citas.domain.schedule;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * UNICO sitio de las reglas de agenda que antes vivian copiadas (LOOP_03, "una regla, un sitio"):
 * la rejilla de slots, el encaje de slots consecutivos dentro de un bloque y el criterio de "ya
 * empezo". {@link AvailabilityBlock}, {@code Appointment} y {@code TimeSlot} delegan aqui; la
 * consulta SQL de oferta ({@code JdbcAvailabilityQueries}) filtra por eficiencia con esta misma
 * regla y una prueba de equivalencia la ata a ella.
 */
public final class AgendaRules {

    /** Duracion de un slot (RF-08). Las horas de bloques y citas caen en multiplos de esto. */
    public static final int SLOT_MINUTES = 30;

    private AgendaRules() {
    }

    /** La hora cae en la rejilla de slots: minuto multiplo de 30, sin segundos ni nanos. */
    public static boolean isOnGrid(LocalTime time) {
        return time.getMinute() % SLOT_MINUTES == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    /**
     * RN-06: "ya empezo" es que el inicio no es posterior a ahora (el instante exacto cuenta como
     * empezado). Criterio unico de bloques, citas y franjas.
     */
    public static boolean hasStarted(LocalDateTime startsAt, LocalDateTime now) {
        return !startsAt.isAfter(now);
    }

    /**
     * RN-05 / D9: {@code slots} slots consecutivos de 30 min desde {@code start} caben enteros en el
     * bloque {@code [blockStart, blockEnd]}: el inicio cae en la rejilla, no es anterior al bloque y
     * el fin no lo excede. Los slots de un bloque son exactamente su rejilla, asi que "consecutivos
     * dentro del mismo bloque" equivale a este encaje.
     */
    public static boolean fits(LocalTime blockStart, LocalTime blockEnd, LocalTime start, int slots) {
        if (!isOnGrid(start) || start.isBefore(blockStart)) {
            return false;
        }
        LocalTime end = start.plusMinutes((long) SLOT_MINUTES * slots);
        return !end.isAfter(blockEnd) && end.isAfter(start);
    }
}
