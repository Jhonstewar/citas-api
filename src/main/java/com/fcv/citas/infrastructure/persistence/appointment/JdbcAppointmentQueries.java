package com.fcv.citas.infrastructure.persistence.appointment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.appointment.AppointmentQueries;
import com.fcv.citas.application.shared.Refs.PatientRef;
import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;

/**
 * Lado de lectura de citas con SQL plano (decision D14): una vista resuelve en una sola consulta
 * los nombres de sede, profesional, especialidad y paciente, sin cargar agregados JPA.
 */
@Component
class JdbcAppointmentQueries implements AppointmentQueries {

    private static final String COLUMNS = """
                   a.id, st.code AS status, st.name AS status_name,
                   a.scheduled_date, a.start_time, a.end_time,
                   TIME_TO_SEC(TIMEDIFF(a.end_time, a.start_time)) DIV 60 AS duration_minutes,
                   si.id AS site_id, si.code AS site_code, si.name AS site_name,
                   p.id AS professional_id, CONCAT(pu.first_names, ' ', pu.last_names) AS professional_name,
                   sp.id AS specialty_id, sp.code AS specialty_code, sp.name AS specialty_name,
                   ty.code AS specialty_type, sp.duration_minutes AS specialty_duration,
                   a.created_at,
                   u.id AS patient_id, CONCAT(u.first_names, ' ', u.last_names) AS patient_name,
                   dt.code AS patient_document_type, u.document_number AS patient_document,
                   u.email AS patient_email, u.phone AS patient_phone,
                   CASE WHEN st.code = 'REJECTED' THEN (
                       SELECT h.reason FROM appointment_status_history h
                       WHERE h.appointment_id = a.id AND h.status_id = a.status_id
                       ORDER BY h.id DESC LIMIT 1) END AS rejection_reason,
                   EXISTS (SELECT 1 FROM reschedule_requests rq
                           WHERE rq.appointment_id = a.id AND rq.decided_at IS NULL) AS pending_reschedule
            """;

    private static final String FROM = """
            FROM appointments a
            JOIN appointment_statuses st ON st.id = a.status_id
            JOIN sites si ON si.id = a.site_id
            JOIN professionals p ON p.id = a.professional_id
            JOIN users pu ON pu.id = p.user_id
            JOIN specialties sp ON sp.id = a.specialty_id
            JOIN appointment_types ty ON ty.id = sp.appointment_type_id
            JOIN users u ON u.id = a.patient_user_id
            JOIN document_types dt ON dt.id = u.document_type_id
            """;

    private static final String SELECT = "SELECT " + COLUMNS + FROM;

    /** Columnas de una solicitud de reprogramacion ({@code RescheduleRequest}, V3 + V10). */
    private static final String RESCHEDULE_COLUMNS = """
                   rr.id AS rr_id, rr.appointment_id AS rr_appointment_id, rs.code AS rr_status,
                   rs.name AS rr_status_name,
                   rr.previous_date, rr.previous_start_time, rr.previous_end_time,
                   ps.id AS previous_site_id, ps.code AS previous_site_code, ps.name AS previous_site_name,
                   rr.proposed_date, rr.proposed_start_time, rr.proposed_end_time,
                   qs.id AS proposed_site_id, qs.code AS proposed_site_code, qs.name AS proposed_site_name,
                   rr.request_reason, rr.decision_reason, rr.created_at AS rr_created_at, rr.decided_at
            """;

    private static final String RESCHEDULE_JOINS = """
             JOIN reschedule_statuses rs ON rs.id = rr.status_id
             JOIN sites ps ON ps.id = rr.previous_site_id
             JOIN sites qs ON qs.id = rr.proposed_site_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAppointmentQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AppointmentView> findByPatient(long patientUserId, String status, LocalDate date) {
        MapSqlParameterSource params = new MapSqlParameterSource("patient", patientUserId);
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE a.patient_user_id = :patient");
        if (status != null && !status.isBlank()) {
            sql.append(" AND st.code = :status");
            params.addValue("status", status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (date != null) {
            sql.append(" AND a.scheduled_date = :date");
            params.addValue("date", date);
        }
        // Proximas primero: las futuras en orden ascendente y despues las pasadas, mas recientes antes.
        sql.append(" ORDER BY (a.scheduled_date < CURRENT_DATE) ASC,"
                + " CASE WHEN a.scheduled_date >= CURRENT_DATE THEN a.scheduled_date END ASC,"
                + " a.scheduled_date DESC, a.start_time ASC");
        return jdbc.query(sql.toString(), params, (rs, i) -> map(rs));
    }

    @Override
    public Optional<AppointmentView> findById(long appointmentId) {
        return jdbc.query(SELECT + " WHERE a.id = :id", new MapSqlParameterSource("id", appointmentId),
                (rs, i) -> map(rs)).stream().findFirst();
    }

    @Override
    public List<HistoryView> history(long appointmentId) {
        return jdbc.query("""
                SELECT st.code AS status, st.name AS status_name, h.source,
                       CASE WHEN u.id IS NULL THEN NULL ELSE CONCAT(u.first_names, ' ', u.last_names) END AS actor_name,
                       h.reason, h.changed_at
                FROM appointment_status_history h
                JOIN appointment_statuses st ON st.id = h.status_id
                LEFT JOIN users u ON u.id = h.actor_user_id
                WHERE h.appointment_id = :id
                ORDER BY h.changed_at ASC, h.id ASC
                """, new MapSqlParameterSource("id", appointmentId),
                (rs, i) -> new HistoryView(rs.getString("status"), rs.getString("status_name"),
                        rs.getString("source"), rs.getString("actor_name"), rs.getString("reason"),
                        rs.getObject("changed_at", LocalDateTime.class)));
    }

    @Override
    public List<AppointmentView> findApprovedByProfessional(long professionalId, LocalDate from, LocalDate to,
            Integer siteId) {
        MapSqlParameterSource params = new MapSqlParameterSource("professional", professionalId)
                .addValue("from", from).addValue("to", to);
        StringBuilder sql = new StringBuilder(SELECT).append("""
                 WHERE a.professional_id = :professional AND st.code = 'APPROVED'
                   AND a.scheduled_date BETWEEN :from AND :to""");
        if (siteId != null) {
            sql.append(" AND a.site_id = :site");
            params.addValue("site", siteId);
        }
        sql.append(" ORDER BY a.scheduled_date, a.start_time, a.id");
        return jdbc.query(sql.toString(), params, (rs, i) -> map(rs));
    }

    @Override
    public List<AppointmentView> findPendingRequests(InboxFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>(List.of("st.code = 'REQUESTED'"));
        if (filter.siteId() != null) {
            where.add("a.site_id = :site");
            params.addValue("site", filter.siteId());
        }
        if (filter.professionalId() != null) {
            where.add("a.professional_id = :professional");
            params.addValue("professional", filter.professionalId());
        }
        if (filter.specialtyId() != null) {
            where.add("a.specialty_id = :specialty");
            params.addValue("specialty", filter.specialtyId());
        }
        if (filter.date() != null) {
            where.add("a.scheduled_date = :date");
            params.addValue("date", filter.date());
        }
        String sql = SELECT + " WHERE " + String.join(" AND ", where) + " ORDER BY a.scheduled_date, a.start_time";
        return jdbc.query(sql, params, (rs, i) -> map(rs));
    }

    @Override
    public List<PendingRescheduleView> findPendingReschedules(InboxFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>(List.of("rs.code = 'PENDING'"));
        // D24: sede y fecha sobre la franja PROPUESTA, que es lo que el ADMIN decide.
        if (filter.siteId() != null) {
            where.add("rr.proposed_site_id = :site");
            params.addValue("site", filter.siteId());
        }
        if (filter.date() != null) {
            where.add("rr.proposed_date = :date");
            params.addValue("date", filter.date());
        }
        // Profesional y especialidad: los de la cita, que la reprogramacion conserva (RF-15).
        if (filter.professionalId() != null) {
            where.add("a.professional_id = :professional");
            params.addValue("professional", filter.professionalId());
        }
        if (filter.specialtyId() != null) {
            where.add("a.specialty_id = :specialty");
            params.addValue("specialty", filter.specialtyId());
        }
        String sql = "SELECT " + COLUMNS + ", " + RESCHEDULE_COLUMNS + FROM
                + " JOIN reschedule_requests rr ON rr.appointment_id = a.id" + RESCHEDULE_JOINS
                + " WHERE " + String.join(" AND ", where)
                + " ORDER BY rr.proposed_date, rr.proposed_start_time, rr.id";
        return jdbc.query(sql, params, (rs, i) -> new PendingRescheduleView(map(rs), mapReschedule(rs)));
    }

    @Override
    public Optional<RescheduleView> findReschedule(long rescheduleRequestId) {
        return jdbc.query("SELECT " + RESCHEDULE_COLUMNS + " FROM reschedule_requests rr" + RESCHEDULE_JOINS
                + " WHERE rr.id = :id", new MapSqlParameterSource("id", rescheduleRequestId),
                (rs, i) -> mapReschedule(rs)).stream().findFirst();
    }

    @Override
    public Optional<RescheduleView> lastReschedule(long appointmentId) {
        return jdbc.query("SELECT " + RESCHEDULE_COLUMNS + " FROM reschedule_requests rr" + RESCHEDULE_JOINS
                + " WHERE rr.appointment_id = :id ORDER BY rr.created_at DESC, rr.id DESC LIMIT 1",
                new MapSqlParameterSource("id", appointmentId), (rs, i) -> mapReschedule(rs)).stream().findFirst();
    }

    @Override
    public Summary summary(LocalDate today) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM appointments a
                     JOIN appointment_statuses st ON st.id = a.status_id
                     WHERE st.code = 'REQUESTED') AS pending,
                  (SELECT COUNT(*) FROM professionals WHERE active) AS professionals,
                  (SELECT COUNT(*) FROM specialties WHERE active) AS specialties,
                  (SELECT COUNT(*) FROM appointments a
                     JOIN appointment_statuses st ON st.id = a.status_id
                     WHERE a.scheduled_date = :today AND st.code IN ('REQUESTED', 'APPROVED')) AS today
                  , (SELECT COUNT(*) FROM reschedule_requests WHERE decided_at IS NULL) AS pending_reschedules
                """, new MapSqlParameterSource("today", today),
                (rs, i) -> new Summary(rs.getLong("pending"), rs.getLong("professionals"),
                        rs.getLong("specialties"), rs.getLong("today"), rs.getLong("pending_reschedules")));
    }

    private static RescheduleView mapReschedule(ResultSet rs) throws SQLException {
        return new RescheduleView(
                rs.getLong("rr_id"),
                rs.getLong("rr_appointment_id"),
                rs.getString("rr_status"),
                rs.getString("rr_status_name"),
                new TimeSlotView(rs.getObject("previous_date", LocalDate.class),
                        rs.getObject("previous_start_time", LocalTime.class),
                        rs.getObject("previous_end_time", LocalTime.class),
                        new SiteRef(rs.getInt("previous_site_id"), rs.getString("previous_site_code"),
                                rs.getString("previous_site_name"))),
                new TimeSlotView(rs.getObject("proposed_date", LocalDate.class),
                        rs.getObject("proposed_start_time", LocalTime.class),
                        rs.getObject("proposed_end_time", LocalTime.class),
                        new SiteRef(rs.getInt("proposed_site_id"), rs.getString("proposed_site_code"),
                                rs.getString("proposed_site_name"))),
                rs.getString("request_reason"),
                rs.getString("decision_reason"),
                rs.getObject("rr_created_at", LocalDateTime.class),
                rs.getObject("decided_at", LocalDateTime.class));
    }

    private static AppointmentView map(ResultSet rs) throws SQLException {
        return new AppointmentView(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getString("status_name"),
                rs.getObject("scheduled_date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getInt("duration_minutes"),
                new SiteRef(rs.getInt("site_id"), rs.getString("site_code"), rs.getString("site_name")),
                new PersonRef(rs.getLong("professional_id"), rs.getString("professional_name")),
                new SpecialtyRef(rs.getInt("specialty_id"), rs.getString("specialty_code"),
                        rs.getString("specialty_name"), rs.getString("specialty_type"),
                        rs.getInt("specialty_duration")),
                rs.getString("rejection_reason"),
                rs.getObject("created_at", LocalDateTime.class),
                new PatientRef(rs.getLong("patient_id"), rs.getString("patient_name"),
                        rs.getString("patient_document_type"), rs.getString("patient_document"),
                        rs.getString("patient_email"), rs.getString("patient_phone")),
                rs.getBoolean("pending_reschedule"));
    }
}
