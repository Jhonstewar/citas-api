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

    /**
     * Todos los filtros salvo la fecha son opcionales ({@code null} = todos), pero el caso de uso exige
     * especialidad o tipo de cita (RF-10). {@code appointmentType}: {@code GENERAL} o {@code SPECIALIZED}.
     */
    List<Offer> offers(Integer specialtyId, String appointmentType, LocalDate date, Integer siteId,
            Long professionalId, LocalDateTime now);

    List<DayCount> days(Integer specialtyId, String appointmentType, LocalDate from, LocalDate to,
            Integer siteId, Long professionalId,
            LocalDateTime now);
}
