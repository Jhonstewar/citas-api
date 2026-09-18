package com.fcv.citas.infrastructure.rest.appointment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.appointment.AdminAppointmentsUseCase;
import com.fcv.citas.application.appointment.AppointmentQueries.InboxFilter;
import com.fcv.citas.application.appointment.AppointmentQueries.Summary;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.Detail;
import com.fcv.citas.infrastructure.rest.CurrentUser;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.AdminAppointmentResponse;

/** HU-029 y HU-030: bandeja y decision del ADMIN. Solo ADMIN (prefijo {@code /api/admin}). */
@RestController
@RequestMapping("/api/admin")
class AdminAppointmentController {

    /** Sin {@code @NotBlank}: el dominio valida el motivo y responde 400 con {@code fieldErrors.reason}. */
    record RejectRequest(String reason) {
    }

    record InboxEntry(String type, AdminAppointmentResponse appointment) {
    }

    private final AdminAppointmentsUseCase admin;

    AdminAppointmentController(AdminAppointmentsUseCase admin) {
        this.admin = admin;
    }

    @GetMapping("/inbox")
    List<InboxEntry> inbox(@RequestParam(required = false) Integer siteId,
            @RequestParam(required = false) Long professionalId, @RequestParam(required = false) Integer specialtyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return admin.inbox(new InboxFilter(siteId, professionalId, specialtyId, date)).stream()
                .map(v -> new InboxEntry("APPOINTMENT_REQUEST", AdminAppointmentResponse.from(v, List.of())))
                .toList();
    }

    @GetMapping("/appointments/{id}")
    AdminAppointmentResponse detail(@PathVariable long id) {
        return response(admin.detail(id));
    }

    @PostMapping("/appointments/{id}/approve")
    AdminAppointmentResponse approve(JwtAuthenticationToken auth, @PathVariable long id) {
        return response(admin.approve(CurrentUser.id(auth), id));
    }

    @PostMapping("/appointments/{id}/reject")
    AdminAppointmentResponse reject(JwtAuthenticationToken auth, @PathVariable long id,
            @RequestBody(required = false) RejectRequest request) {
        return response(admin.reject(CurrentUser.id(auth), id, request == null ? null : request.reason()));
    }

    @GetMapping("/summary")
    Summary summary() {
        return admin.summary();
    }

    private static AdminAppointmentResponse response(Detail detail) {
        return AdminAppointmentResponse.from(detail.appointment(), detail.history());
    }
}
