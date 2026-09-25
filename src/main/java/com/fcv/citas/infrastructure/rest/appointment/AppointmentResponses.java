package com.fcv.citas.infrastructure.rest.appointment;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.appointment.AppointmentQueries.HistoryView;
import com.fcv.citas.application.appointment.AppointmentQueries.RescheduleView;
import com.fcv.citas.application.appointment.AppointmentQueries.TimeSlotView;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.PatientAppointment;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase.PatientDetail;
import com.fcv.citas.application.shared.Refs.PatientRef;
import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;

/**
 * Cuerpos JSON de citas de los contratos S3 y S4 ({@code contrato-rest-citas}). Fechas ISO y horas
 * {@code HH:mm}. El paciente nunca recibe el bloque {@code patient}; el ADMIN si. Las acciones del
 * paciente ({@code cancellable}, {@code reschedulable}) solo viajan al paciente (aclaracion 5 de S4).
 * {@code lastReschedule} (HU-028) se omite si la cita nunca tuvo una solicitud: los campos nulos no se
 * emiten (aclaracion 1 de S4).
 */
public final class AppointmentResponses {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private AppointmentResponses() {
    }

    public static String time(LocalTime time) {
        return time.format(HH_MM);
    }

    /**
     * {@code Appointment} del contrato. {@code cancellable} lo lleva el listado del paciente
     * (aclaracion 8 de S4); es {@code null} —y no se emite— donde no aplica.
     */
    public record AppointmentResponse(long id, String status, String statusName, String date, String startTime,
            String endTime, int durationMinutes, SiteRef site, PersonRef professional, SpecialtyRef specialty,
            String rejectionReason, LocalDateTime createdAt, boolean pendingReschedule, Boolean cancellable) {

        public static AppointmentResponse from(PatientAppointment a) {
            return from(a.appointment(), a.cancellable());
        }

        static AppointmentResponse from(AppointmentView v, Boolean cancellable) {
            return new AppointmentResponse(v.id(), v.status(), v.statusName(), v.date().toString(),
                    time(v.startTime()), time(v.endTime()), v.durationMinutes(), v.site(), v.professional(),
                    v.specialty(), v.rejectionReason(), v.createdAt(), v.pendingReschedule(), cancellable);
        }
    }

    public record HistoryResponse(String status, String statusName, String source, String actorName, String reason,
            LocalDateTime changedAt) {

        public static HistoryResponse from(HistoryView h) {
            return new HistoryResponse(h.status(), h.statusName(), h.source(), h.actorName(), h.reason(),
                    h.changedAt());
        }

        static List<HistoryResponse> all(List<HistoryView> history) {
            return history.stream().map(HistoryResponse::from).toList();
        }
    }

    /** {@code AppointmentDetail} del paciente: la cita, su historial y lo que puede hacer ahora (S4). */
    public record AppointmentDetailResponse(long id, String status, String statusName, String date,
            String startTime, String endTime, int durationMinutes, SiteRef site, PersonRef professional,
            SpecialtyRef specialty, String rejectionReason, LocalDateTime createdAt, boolean pendingReschedule,
            boolean cancellable, boolean reschedulable, List<HistoryResponse> history,
            RescheduleResponse lastReschedule) {

        public static AppointmentDetailResponse from(PatientDetail d) {
            AppointmentView v = d.appointment();
            return new AppointmentDetailResponse(v.id(), v.status(), v.statusName(), v.date().toString(),
                    time(v.startTime()), time(v.endTime()), v.durationMinutes(), v.site(), v.professional(),
                    v.specialty(), v.rejectionReason(), v.createdAt(), v.pendingReschedule(), d.cancellable(),
                    d.reschedulable(), HistoryResponse.all(d.history()), RescheduleResponse.of(d.lastReschedule()));
        }
    }

    /** {@code AdminAppointment} del contrato: detalle mas los datos del paciente, sin acciones del paciente. */
    public record AdminAppointmentResponse(long id, String status, String statusName, String date,
            String startTime, String endTime, int durationMinutes, SiteRef site, PersonRef professional,
            SpecialtyRef specialty, String rejectionReason, LocalDateTime createdAt, boolean pendingReschedule,
            List<HistoryResponse> history, PatientRef patient, RescheduleResponse lastReschedule) {

        public static AdminAppointmentResponse from(AppointmentView v, List<HistoryView> history,
                RescheduleView lastReschedule) {
            return new AdminAppointmentResponse(v.id(), v.status(), v.statusName(), v.date().toString(),
                    time(v.startTime()), time(v.endTime()), v.durationMinutes(), v.site(), v.professional(),
                    v.specialty(), v.rejectionReason(), v.createdAt(), v.pendingReschedule(),
                    HistoryResponse.all(history), v.patient(), RescheduleResponse.of(lastReschedule));
        }
    }

    /** {@code TimeSlot} del contrato S4: dia ISO, horas {@code HH:mm} y sede. */
    public record TimeSlotResponse(String date, String startTime, String endTime, SiteRef site) {

        static TimeSlotResponse from(TimeSlotView s) {
            return new TimeSlotResponse(s.date().toString(), time(s.startTime()), time(s.endTime()), s.site());
        }
    }

    /**
     * {@code RescheduleRequest} del contrato S4 (HU-027, HU-028, HU-031). {@code requestReason},
     * {@code decisionReason} y {@code decidedAt} se omiten cuando son nulos.
     */
    public record RescheduleResponse(long id, long appointmentId, String status, String statusName,
            TimeSlotResponse previous, TimeSlotResponse proposed, String requestReason, String decisionReason,
            LocalDateTime createdAt, LocalDateTime decidedAt) {

        public static RescheduleResponse from(RescheduleView r) {
            return new RescheduleResponse(r.id(), r.appointmentId(), r.status(), r.statusName(),
                    TimeSlotResponse.from(r.previous()), TimeSlotResponse.from(r.proposed()), r.requestReason(),
                    r.decisionReason(), r.createdAt(), r.decidedAt());
        }

        /** {@code null} si no hay solicitud: el campo no se emite. */
        static RescheduleResponse of(RescheduleView r) {
            return r == null ? null : from(r);
        }
    }
}
