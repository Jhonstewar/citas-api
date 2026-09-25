package com.fcv.citas.application.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.fcv.citas.application.shared.Refs.PatientRef;
import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;

/**
 * Puerto de lectura de citas (HU-025, HU-029, HU-032). Devuelve vistas ya resueltas con nombres
 * de sede, profesional y especialidad; las escrituras van por {@code AppointmentRepository}.
 */
public interface AppointmentQueries {

    record AppointmentView(
            long id,
            String status,
            String statusName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int durationMinutes,
            SiteRef site,
            PersonRef professional,
            SpecialtyRef specialty,
            String rejectionReason,
            LocalDateTime createdAt,
            PatientRef patient,
            /** Hay una solicitud de reprogramacion sin decidir (contrato S4, D20). */
            boolean pendingReschedule) {
    }

    record HistoryView(String status, String statusName, String source, String actorName, String reason,
            LocalDateTime changedAt) {
    }

    /** Filtros opcionales de la bandeja: {@code null} = sin filtrar por ese campo. */
    record InboxFilter(Integer siteId, Long professionalId, Integer specialtyId, LocalDate date) {
    }

    /** {@code pendingReschedules}: solicitudes de reprogramacion sin decidir (contrato S4). */
    record Summary(long pendingRequests, long activeProfessionals, long activeSpecialties, long appointmentsToday,
            long pendingReschedules) {
    }

    /** {@code TimeSlot} del contrato S4: dia, horas y sede resuelta. */
    record TimeSlotView(LocalDate date, LocalTime startTime, LocalTime endTime, SiteRef site) {
    }

    /** {@code RescheduleRequest} del contrato S4. {@code decidedAt} es nulo mientras esta PENDING. */
    record RescheduleView(long id, long appointmentId, String status, String statusName, TimeSlotView previous,
            TimeSlotView proposed, String requestReason, String decisionReason, LocalDateTime createdAt,
            LocalDateTime decidedAt) {
    }

    /** Entrada {@code RESCHEDULE_REQUEST} de la bandeja: la cita (franja actual) y la solicitud. */
    record PendingRescheduleView(AppointmentView appointment, RescheduleView reschedule) {
    }

    /** Citas del paciente, proximas primero. {@code status} y {@code date} opcionales. */
    List<AppointmentView> findByPatient(long patientUserId, String status, LocalDate date);

    Optional<AppointmentView> findById(long appointmentId);

    /** Historial en orden cronologico (HU-032 CA-07). */
    List<HistoryView> history(long appointmentId);

    /**
     * HU-020: citas {@code APPROVED} del profesional entre dos fechas (inclusive), opcionalmente de una
     * sede, por fecha y hora. El filtro de estado vive en la consulta, no en el cliente (DoD).
     */
    List<AppointmentView> findApprovedByProfessional(long professionalId, LocalDate from, LocalDate to,
            Integer siteId);

    /** Citas especializadas en {@code REQUESTED}, las mas proximas primero (HU-029). */
    List<AppointmentView> findPendingRequests(InboxFilter filter);

    /**
     * HU-029 (D24): solicitudes de reprogramacion {@code PENDING}. Sede y fecha filtran la franja
     * PROPUESTA; profesional y especialidad, los de la cita (que la solicitud conserva).
     */
    List<PendingRescheduleView> findPendingReschedules(InboxFilter filter);

    Optional<RescheduleView> findReschedule(long rescheduleRequestId);

    /** HU-028: la solicitud mas reciente de la cita, en cualquier estado ({@code lastReschedule}). */
    Optional<RescheduleView> lastReschedule(long appointmentId);

    Summary summary(LocalDate today);
}
