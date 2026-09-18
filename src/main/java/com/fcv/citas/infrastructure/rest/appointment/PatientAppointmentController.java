package com.fcv.citas.infrastructure.rest.appointment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.appointment.PatientAppointmentsUseCase;
import com.fcv.citas.infrastructure.rest.CurrentUser;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.AppointmentDetailResponse;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.AppointmentResponse;

/** HU-025: mis citas y detalle. Solo USER (prefijo {@code /api/patient}). */
@RestController
@RequestMapping("/api/patient/appointments")
class PatientAppointmentController {

    private final PatientAppointmentsUseCase appointments;

    PatientAppointmentController(PatientAppointmentsUseCase appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    List<AppointmentResponse> list(JwtAuthenticationToken auth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointments.list(CurrentUser.id(auth), status, date).stream().map(AppointmentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    AppointmentDetailResponse detail(JwtAuthenticationToken auth, @PathVariable long id) {
        PatientAppointmentsUseCase.Detail detail = appointments.detail(CurrentUser.id(auth), id);
        return AppointmentDetailResponse.from(detail.appointment(), detail.history());
    }
}
