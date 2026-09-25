package com.fcv.citas.infrastructure.rest.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.appointment.CancelAppointmentUseCase;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase;
import com.fcv.citas.application.appointment.RescheduleAppointmentUseCase;
import com.fcv.citas.application.appointment.RescheduleAppointmentUseCase.RescheduleCommand;
import com.fcv.citas.infrastructure.rest.CurrentUser;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.AppointmentDetailResponse;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.AppointmentResponse;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.RescheduleResponse;

/** HU-025 (mis citas y detalle), HU-026 (cancelar) y HU-027 (pedir reprogramacion). Solo USER (prefijo {@code /api/patient}). */
@RestController
@RequestMapping("/api/patient/appointments")
class PatientAppointmentController {

    /**
     * Cuerpo opcional de la cancelacion (aclaracion 2 de S4): sin cuerpo, {@code {}} y
     * {@code {"reason": ""}} equivalen a no dar motivo. Sin anotaciones de validacion: el dominio
     * limita el motivo a 500 caracteres y responde 400 con {@code fieldErrors.reason}.
     */
    record CancelRequest(String reason) {
    }

    /**
     * HU-027: franja nueva (sede, dia y hora) y motivo opcional (≤ 500). Sin anotaciones de
     * validacion: el caso de uso responde 400 con {@code fieldErrors} (igual que la reserva).
     * {@code professionalId} y {@code specialtyId} no forman parte del contrato: si llegan distintos de
     * los de la cita, 422 {@code WRONG_FLOW} (CA-02, RF-15: cambiar de profesional es una cita nueva).
     */
    record RescheduleBody(Integer siteId, LocalDate date, LocalTime startTime, String reason, Long professionalId,
            Integer specialtyId) {
    }

    private final PatientAppointmentsUseCase appointments;
    private final CancelAppointmentUseCase cancellation;
    private final RescheduleAppointmentUseCase reschedule;

    PatientAppointmentController(PatientAppointmentsUseCase appointments, CancelAppointmentUseCase cancellation,
            RescheduleAppointmentUseCase reschedule) {
        this.appointments = appointments;
        this.cancellation = cancellation;
        this.reschedule = reschedule;
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
        return AppointmentDetailResponse.from(appointments.detail(CurrentUser.id(auth), id));
    }

    /** HU-026: 200 con el detalle ya {@code CANCELLED} · 404 · 409 INVALID_TRANSITION / APPOINTMENT_EXPIRED. */
    @PostMapping("/{id}/cancel")
    AppointmentDetailResponse cancel(JwtAuthenticationToken auth, @PathVariable long id,
            @RequestBody(required = false) CancelRequest request) {
        return AppointmentDetailResponse.from(
                cancellation.cancel(CurrentUser.id(auth), id, request == null ? null : request.reason()));
    }

    /**
     * HU-027: 201 {@code RescheduleRequest} {@code PENDING} · 400 · 404 · 409 {@code INVALID_TRANSITION} /
     * {@code RESCHEDULE_PENDING} / {@code APPOINTMENT_EXPIRED} / {@code SLOT_TAKEN} · 422 {@code PAST_TIME} /
     * {@code SAME_SLOT} / {@code SLOT_NOT_AVAILABLE} / {@code SITE_NOT_ASSIGNED} / {@code WRONG_FLOW}...
     */
    @PostMapping("/{id}/reschedule")
    @ResponseStatus(HttpStatus.CREATED)
    RescheduleResponse reschedule(JwtAuthenticationToken auth, @PathVariable long id,
            @RequestBody RescheduleBody body) {
        return RescheduleResponse.from(reschedule.request(CurrentUser.id(auth), id, new RescheduleCommand(
                body.siteId(), body.date(), body.startTime(), body.reason(), body.professionalId(),
                body.specialtyId())));
    }
}
