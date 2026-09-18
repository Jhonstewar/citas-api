package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import com.fcv.citas.application.appointment.AvailabilityQueries.DayCount;
import com.fcv.citas.application.appointment.AvailabilityQueries.Offer;
import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-022 / RF-10: busqueda de disponibilidad con filtros de sede, tipo de cita, especialidad,
 * profesional y fecha. La especialidad es opcional si se indica el tipo de cita: cada franja
 * informa entonces su especialidad y su duracion. Solo lectura.
 */
public class SearchAvailabilityUseCase {

    static final int MAX_RANGE_DAYS = 62;

    private final AvailabilityQueries queries;
    private final Clock clock;

    public SearchAvailabilityUseCase(AvailabilityQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public List<Offer> offers(Integer specialtyId, String appointmentType, LocalDate date, Integer siteId,
            Long professionalId) {
        return queries.offers(specialtyId, type(specialtyId, appointmentType), date, siteId, professionalId,
                SystemZone.now(clock));
    }

    public List<DayCount> days(Integer specialtyId, String appointmentType, LocalDate from, LocalDate to,
            Integer siteId, Long professionalId) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw InvalidRequestException.field("to", "El rango debe ser válido y de máximo " + MAX_RANGE_DAYS + " días");
        }
        return queries.days(specialtyId, type(specialtyId, appointmentType), from, to, siteId, professionalId,
                SystemZone.now(clock));
    }

    /** Exige especialidad o tipo de cita, y valida el tipo contra el catalogo fijo. */
    private static String type(Integer specialtyId, String appointmentType) {
        if (appointmentType == null || appointmentType.isBlank()) {
            if (specialtyId == null) {
                throw InvalidRequestException.field("specialtyId", "Indique la especialidad o el tipo de cita");
            }
            return null;
        }
        try {
            return AppointmentType.valueOf(appointmentType.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw InvalidRequestException.field("appointmentType", "El tipo de cita debe ser GENERAL o SPECIALIZED");
        }
    }
}
