package com.fcv.citas.infrastructure.rest.automation;

import java.util.List;

import com.fcv.citas.application.automation.AutomationQueriesUseCase.DailySummary;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses;

/**
 * Contrato de {@code GET /api/automation/appointments/daily} (HU-034 CA-11, WF-003). Sin PII: ni nombres,
 * ni correo, ni documento, ni telefono, ni ids de paciente o de cita.
 */
record DailySummaryResponse(String date, List<Row> rows, Pending pending) {

    record Row(String siteCode, String status, String specialty, String startTime) {
    }

    record Pending(long pendingRequests, long pendingReschedules) {
    }

    static DailySummaryResponse from(DailySummary s) {
        return new DailySummaryResponse(s.date().toString(),
                s.rows().stream().map(r -> new Row(r.siteCode(), r.status(), r.specialty(),
                        AppointmentResponses.time(r.startTime()))).toList(),
                new Pending(s.pendingRequests(), s.pendingReschedules()));
    }
}
