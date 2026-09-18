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
            PatientRef patient) {
    }

    record HistoryView(String status, String statusName, String source, String actorName, String reason,
            LocalDateTime changedAt) {
    }

    /** Filtros opcionales de la bandeja: {@code null} = sin filtrar por ese campo. */
    record InboxFilter(Integer siteId, Long professionalId, Integer specialtyId, LocalDate date) {
    }

    record Summary(long pendingRequests, long activeProfessionals, long activeSpecialties, long appointmentsToday) {
    }

    /** Citas del paciente, proximas primero. {@code status} y {@code date} opcionales. */
    List<AppointmentView> findByPatient(long patientUserId, String status, LocalDate date);

    Optional<AppointmentView> findById(long appointmentId);

    /** Historial en orden cronologico (HU-032 CA-07). */
    List<HistoryView> history(long appointmentId);

    /** Citas especializadas en {@code REQUESTED}, las mas proximas primero (HU-029). */
    List<AppointmentView> findPendingRequests(InboxFilter filter);

    Summary summary(LocalDate today);
}
