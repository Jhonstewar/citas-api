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
import com.fcv.citas.application.appointment.AdminAppointmentsUseCase.InboxItem;
import com.fcv.citas.application.appointment.AppointmentQueries.InboxFilter;
import com.fcv.citas.application.appointment.AppointmentQueries.Summary;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.Detail;
import com.fcv.citas.application.appointment.RescheduleAppointmentUseCase;
import com.fcv.citas.infrastructure.rest.CurrentUser;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.AdminAppointmentResponse;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.RescheduleResponse;

/**
 * HU-029 (bandeja), HU-030 (decidir una cita especializada) y HU-031 (decidir una reprogramacion).
 * Solo ADMIN (prefijo {@code /api/admin}).
 */
@RestController
@RequestMapping("/api/admin")
class AdminAppointmentController {

    /** Sin {@code @NotBlank}: el dominio valida el motivo y responde 400 con {@code fieldErrors.reason}. */
    record RejectRequest(String reason) {
    }

    /**
     * {@code InboxEntry} del contrato S4: union por {@code type}. {@code reschedule} solo viaja en
     * {@code RESCHEDULE_REQUEST} (nulo = omitido). En esa entrada la cita trae su {@code lastReschedule},
     * que es la propia solicitud pendiente.
     */
    record InboxEntry(String type, AdminAppointmentResponse appointment, RescheduleResponse reschedule) {

        static InboxEntry from(InboxItem item) {
            return new InboxEntry(item.type().name(),
                    AdminAppointmentResponse.from(item.appointment(), List.of(), item.reschedule()),
                    item.reschedule() == null ? null : RescheduleResponse.from(item.reschedule()));
        }
    }

    private final AdminAppointmentsUseCase admin;
    private final RescheduleAppointmentUseCase reschedules;

    AdminAppointmentController(AdminAppointmentsUseCase admin, RescheduleAppointmentUseCase reschedules) {
        this.admin = admin;
        this.reschedules = reschedules;
    }

    /** HU-029: en una reprogramacion, {@code siteId} y {@code date} filtran la franja PROPUESTA (D24). */
    @GetMapping("/inbox")
    List<InboxEntry> inbox(@RequestParam(required = false) Integer siteId,
            @RequestParam(required = false) Long professionalId, @RequestParam(required = false) Integer specialtyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String type) {
        return admin.inbox(new InboxFilter(siteId, professionalId, specialtyId, date), type).stream()
                .map(InboxEntry::from).toList();
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

    /**
     * HU-031: el id es el de la SOLICITUD. 200 {@code AdminAppointment} ya movido · 404 · 409
     * {@code INVALID_TRANSITION} / {@code APPOINTMENT_EXPIRED} / {@code CONCURRENT_CHANGE}.
     */
    @PostMapping("/reschedules/{id}/approve")
    AdminAppointmentResponse approveReschedule(JwtAuthenticationToken auth, @PathVariable long id) {
        return response(reschedules.approve(CurrentUser.id(auth), id));
    }

    /** HU-031: 200 {@code AdminAppointment} sin cambios de franja · 400 sin motivo · 404 · 409. */
    @PostMapping("/reschedules/{id}/reject")
    AdminAppointmentResponse rejectReschedule(JwtAuthenticationToken auth, @PathVariable long id,
            @RequestBody(required = false) RejectRequest request) {
        return response(reschedules.reject(CurrentUser.id(auth), id, request == null ? null : request.reason()));
    }

    @GetMapping("/summary")
    Summary summary() {
        return admin.summary();
    }

    private static AdminAppointmentResponse response(Detail detail) {
        return AdminAppointmentResponse.from(detail.appointment(), detail.history(), detail.lastReschedule());
    }
}
