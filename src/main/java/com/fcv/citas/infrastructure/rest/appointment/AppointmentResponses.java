package com.fcv.citas.infrastructure.rest.appointment;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.appointment.AppointmentQueries.HistoryView;
import com.fcv.citas.application.shared.Refs.PatientRef;
import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;

/**
 * Cuerpos JSON de citas del contrato S3 ({@code contrato-rest-citas}). Fechas ISO y horas
 * {@code HH:mm}. El paciente nunca recibe el bloque {@code patient}; el ADMIN si.
 */
public final class AppointmentResponses {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private AppointmentResponses() {
    }

    public static String time(LocalTime time) {
        return time.format(HH_MM);
    }

    public record AppointmentResponse(long id, String status, String statusName, String date, String startTime,
            String endTime, int durationMinutes, SiteRef site, PersonRef professional, SpecialtyRef specialty,
            String rejectionReason, LocalDateTime createdAt) {

        public static AppointmentResponse from(AppointmentView v) {
            return new AppointmentResponse(v.id(), v.status(), v.statusName(), v.date().toString(),
                    time(v.startTime()), time(v.endTime()), v.durationMinutes(), v.site(), v.professional(),
                    v.specialty(), v.rejectionReason(), v.createdAt());
        }
    }

    public record HistoryResponse(String status, String statusName, String source, String actorName, String reason,
            LocalDateTime changedAt) {

        public static HistoryResponse from(HistoryView h) {
            return new HistoryResponse(h.status(), h.statusName(), h.source(), h.actorName(), h.reason(),
                    h.changedAt());
        }
    }

    /** {@code AppointmentDetail} del contrato: la cita mas su historial cronologico. */
    public record AppointmentDetailResponse(long id, String status, String statusName, String date,
            String startTime, String endTime, int durationMinutes, SiteRef site, PersonRef professional,
            SpecialtyRef specialty, String rejectionReason, LocalDateTime createdAt, List<HistoryResponse> history) {

        public static AppointmentDetailResponse from(AppointmentView v, List<HistoryView> history) {
            AppointmentResponse a = AppointmentResponse.from(v);
            return new AppointmentDetailResponse(a.id(), a.status(), a.statusName(), a.date(), a.startTime(),
                    a.endTime(), a.durationMinutes(), a.site(), a.professional(), a.specialty(),
                    a.rejectionReason(), a.createdAt(), history.stream().map(HistoryResponse::from).toList());
        }
    }

    /** {@code AdminAppointment} del contrato: detalle mas los datos del paciente. */
    public record AdminAppointmentResponse(long id, String status, String statusName, String date,
            String startTime, String endTime, int durationMinutes, SiteRef site, PersonRef professional,
            SpecialtyRef specialty, String rejectionReason, LocalDateTime createdAt, List<HistoryResponse> history,
            PatientRef patient) {

        public static AdminAppointmentResponse from(AppointmentView v, List<HistoryView> history) {
            AppointmentDetailResponse d = AppointmentDetailResponse.from(v, history);
            return new AdminAppointmentResponse(d.id(), d.status(), d.statusName(), d.date(), d.startTime(),
                    d.endTime(), d.durationMinutes(), d.site(), d.professional(), d.specialty(),
                    d.rejectionReason(), d.createdAt(), d.history(), v.patient());
        }
    }
}
