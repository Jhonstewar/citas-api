package com.fcv.citas.application.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;

/**
 * Puerto de lectura de la oferta reservable (HU-022). Buscar no retiene nada (CA-08).
 * Una franja se ofrece solo si TODOS sus slots (1 para 30 min, 2 consecutivos del mismo bloque
 * para 60 min) estan libres, son futuros y el profesional y la especialidad estan activos y
 * asociados, en una sede habilitada.
 */
public interface AvailabilityQueries {

    record Offer(PersonRef professional, SiteRef site, SpecialtyRef specialty, LocalDate date, LocalTime startTime,
            LocalTime endTime, int durationMinutes) {
    }

    record DayCount(LocalDate date, int offers) {
    }

    /** {@code siteId} y {@code professionalId} opcionales ({@code null} = todos). */
    List<Offer> offers(int specialtyId, LocalDate date, Integer siteId, Long professionalId, LocalDateTime now);

    List<DayCount> days(int specialtyId, LocalDate from, LocalDate to, Integer siteId, Long professionalId,
            LocalDateTime now);
}
