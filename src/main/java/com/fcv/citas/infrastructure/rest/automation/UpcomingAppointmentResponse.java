package com.fcv.citas.infrastructure.rest.automation;

import com.fcv.citas.application.appointment.AppointmentQueries.UpcomingAppointmentView;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses;

/**
 * Contrato de {@code GET /api/automation/appointments/upcoming} (runbook S5 §2.1). Minimizacion de datos:
 * el correo viaja porque sin el no hay recordatorio; no hay documento, telefono ni historial.
 */
record UpcomingAppointmentResponse(long appointmentId, String patientFirstName, String patientEmail, String date,
        String startTime, String endTime, Site site, String professional, String specialty) {

    record Site(String code, String name, String address) {
    }

    static UpcomingAppointmentResponse from(UpcomingAppointmentView v) {
        return new UpcomingAppointmentResponse(v.appointmentId(), v.patientFirstName(), v.patientEmail(),
                v.date().toString(), AppointmentResponses.time(v.startTime()), AppointmentResponses.time(v.endTime()),
                new Site(v.site().code(), v.site().name(), v.site().address()), v.professional(), v.specialty());
    }
}
