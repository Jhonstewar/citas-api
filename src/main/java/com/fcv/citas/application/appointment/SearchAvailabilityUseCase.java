package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fcv.citas.application.appointment.AvailabilityQueries.DayCount;
import com.fcv.citas.application.appointment.AvailabilityQueries.Offer;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.SystemZone;

/** HU-022: busqueda de disponibilidad con filtros. Solo lectura. */
public class SearchAvailabilityUseCase {

    static final int MAX_RANGE_DAYS = 62;

    private final AvailabilityQueries queries;
    private final Clock clock;

    public SearchAvailabilityUseCase(AvailabilityQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public List<Offer> offers(int specialtyId, LocalDate date, Integer siteId, Long professionalId) {
        return queries.offers(specialtyId, date, siteId, professionalId, SystemZone.now(clock));
    }

    public List<DayCount> days(int specialtyId, LocalDate from, LocalDate to, Integer siteId, Long professionalId) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw InvalidRequestException.field("to", "El rango debe ser válido y de máximo " + MAX_RANGE_DAYS + " días");
        }
        return queries.days(specialtyId, from, to, siteId, professionalId, SystemZone.now(clock));
    }
}
