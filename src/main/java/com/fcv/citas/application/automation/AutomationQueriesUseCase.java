package com.fcv.citas.application.automation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fcv.citas.application.appointment.AppointmentQueries;
import com.fcv.citas.application.appointment.AppointmentQueries.DailyRowView;
import com.fcv.citas.application.appointment.AppointmentQueries.Summary;
import com.fcv.citas.application.appointment.AppointmentQueries.UpcomingAppointmentView;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-034: citas aprobadas proximas para recordatorios (n8n). La ventana es (ahora, ahora + hours] en
 * hora de America/Bogota. Solo lectura.
 */
public class AutomationQueriesUseCase {

    static final int MIN_HOURS = 1;
    static final int MAX_HOURS = 72;

    private final AppointmentQueries queries;
    private final Clock clock;

    public AutomationQueriesUseCase(AppointmentQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public List<UpcomingAppointmentView> upcoming(int hours) {
        if (hours < MIN_HOURS || hours > MAX_HOURS) {
            throw InvalidRequestException.field("hours",
                    "Las horas deben ser un entero entre " + MIN_HOURS + " y " + MAX_HOURS);
        }
        LocalDateTime from = SystemZone.now(clock);
        return queries.findApprovedStartingBetween(from, from.plusHours(hours));
    }

    /** HU-034 CA-11: resumen del dia para WF-003. Sin PII. {@code pendingRequests/Reschedules} son globales. */
    public record DailySummary(LocalDate date, List<DailyRowView> rows, long pendingRequests,
            long pendingReschedules) {
    }

    /** {@code date} nulo = hoy en America/Bogota. */
    public DailySummary daily(LocalDate date) {
        LocalDate today = SystemZone.today(clock);
        LocalDate day = date == null ? today : date;
        List<DailyRowView> rows = queries.findDailyRows(day);
        Summary summary = queries.summary(today);
        return new DailySummary(day, rows, summary.pendingRequests(), summary.pendingReschedules());
    }
}
