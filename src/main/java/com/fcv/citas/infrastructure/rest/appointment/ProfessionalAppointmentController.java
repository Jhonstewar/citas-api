package com.fcv.citas.infrastructure.rest.appointment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.appointment.ProfessionalAppointmentsUseCase;
import com.fcv.citas.application.appointment.ProfessionalAppointmentsUseCase.ProfessionalAppointment;
import com.fcv.citas.application.appointment.ProfessionalAppointmentsUseCase.ProfessionalPatientRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;
import com.fcv.citas.infrastructure.rest.CurrentUser;

/**
 * HU-020 (agenda de citas aprobadas) y HU-021 (cierre de atencion). Solo PROFESSIONAL (prefijo
 * {@code /api/professional}); el titular sale del token, nunca de un parametro: un
 * {@code professionalId} en la consulta se ignora (HU-020 CA-05).
 */
@RestController
@RequestMapping("/api/professional/appointments")
class ProfessionalAppointmentController {

    /** {@code ProfessionalAppointment} del contrato S4 (sin email ni telefono del paciente, D35). */
    record ProfessionalAppointmentResponse(long id, String status, String statusName, String date,
            String startTime, String endTime, int durationMinutes, SiteRef site, SpecialtyRef specialty,
            ProfessionalPatientRef patient, boolean closable) {

        static ProfessionalAppointmentResponse from(ProfessionalAppointment a) {
            AppointmentView v = a.appointment();
            return new ProfessionalAppointmentResponse(v.id(), v.status(), v.statusName(), v.date().toString(),
                    AppointmentResponses.time(v.startTime()), AppointmentResponses.time(v.endTime()),
                    v.durationMinutes(), v.site(), v.specialty(), a.patient(), a.closable());
        }
    }

    private final ProfessionalAppointmentsUseCase appointments;

    ProfessionalAppointmentController(ProfessionalAppointmentsUseCase appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    List<ProfessionalAppointmentResponse> agenda(JwtAuthenticationToken auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer siteId) {
        return appointments.agenda(CurrentUser.id(auth), from, to, siteId).stream()
                .map(ProfessionalAppointmentResponse::from).toList();
    }

    /** HU-021: 200 con la cita ya {@code COMPLETED} · 404 si no es suya · 409 INVALID_TRANSITION / APPOINTMENT_NOT_STARTED. */
    @PostMapping("/{id}/complete")
    ProfessionalAppointmentResponse complete(JwtAuthenticationToken auth, @PathVariable long id) {
        return ProfessionalAppointmentResponse.from(appointments.complete(CurrentUser.id(auth), id));
    }

    /** HU-021: igual que {@link #complete}, con {@code NO_SHOW}. */
    @PostMapping("/{id}/no-show")
    ProfessionalAppointmentResponse noShow(JwtAuthenticationToken auth, @PathVariable long id) {
        return ProfessionalAppointmentResponse.from(appointments.noShow(CurrentUser.id(auth), id));
    }
}
