package com.fcv.citas.infrastructure.persistence.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.appointment.AvailabilityQueries;
import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;

/**
 * Oferta reservable (HU-022) en una sola consulta (D14). {@code s1} es el primer slot; para 60 min
 * se exige {@code s2}, el slot que empieza 30 min despues en el MISMO bloque (RN-05, D9), y
 * ninguno de los dos puede tener fila en {@code slot_reservations} (RN-01).
 */
@Component
class JdbcAvailabilityQueries implements AvailabilityQueries {

    private static final String FROM_WHERE = """
            FROM availability_slots s1
            JOIN availability_blocks b ON b.id = s1.availability_block_id
            JOIN professionals p ON p.id = b.professional_id AND p.active
            JOIN users pu ON pu.id = p.user_id
            JOIN professional_specialties ps ON ps.professional_id = p.id
            JOIN specialties sp ON sp.id = ps.specialty_id AND sp.active
            JOIN appointment_types ty ON ty.id = sp.appointment_type_id
            JOIN professional_sites psi ON psi.professional_id = p.id AND psi.site_id = b.site_id
            JOIN sites si ON si.id = b.site_id AND si.active
            LEFT JOIN availability_slots s2 ON s2.availability_block_id = b.id
                 AND s2.start_time = ADDTIME(s1.start_time, '00:30:00')
            WHERE NOT EXISTS (SELECT 1 FROM slot_reservations r WHERE r.slot_id = s1.id)
              AND (sp.duration_minutes = 30 OR (s2.id IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM slot_reservations r2 WHERE r2.slot_id = s2.id)))
              AND (b.block_date > :today OR (b.block_date = :today AND s1.start_time > :nowTime))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAvailabilityQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Offer> offers(Integer specialtyId, String appointmentType, LocalDate date, Integer siteId,
            Long professionalId,
            LocalDateTime now) {
        MapSqlParameterSource params = params(specialtyId, appointmentType, siteId, professionalId, now)
                .addValue("date", date);
        String sql = """
                SELECT p.id AS professional_id, CONCAT(pu.first_names, ' ', pu.last_names) AS professional_name,
                       si.id AS site_id, si.code AS site_code, si.name AS site_name,
                       sp.id AS specialty_id, sp.code AS specialty_code, sp.name AS specialty_name,
                       ty.code AS specialty_type, sp.duration_minutes,
                       b.block_date, s1.start_time
                """ + FROM_WHERE + " AND b.block_date = :date" + filters(specialtyId, appointmentType, siteId, professionalId)
                + " ORDER BY s1.start_time, professional_name, specialty_name";
        return jdbc.query(sql, params, (rs, i) -> {
            LocalTime start = rs.getObject("start_time", LocalTime.class);
            int duration = rs.getInt("duration_minutes");
            return new Offer(
                    new PersonRef(rs.getLong("professional_id"), rs.getString("professional_name")),
                    new SiteRef(rs.getInt("site_id"), rs.getString("site_code"), rs.getString("site_name")),
                    new SpecialtyRef(rs.getInt("specialty_id"), rs.getString("specialty_code"),
                            rs.getString("specialty_name"), rs.getString("specialty_type"), duration),
                    rs.getObject("block_date", LocalDate.class), start, start.plusMinutes(duration), duration);
        });
    }

    @Override
    public List<DayCount> days(Integer specialtyId, String appointmentType, LocalDate from, LocalDate to,
            Integer siteId, Long professionalId,
            LocalDateTime now) {
        MapSqlParameterSource params = params(specialtyId, appointmentType, siteId, professionalId, now)
                .addValue("from", from)
                .addValue("to", to);
        String sql = "SELECT b.block_date, COUNT(*) AS offers " + FROM_WHERE
                + " AND b.block_date BETWEEN :from AND :to"
                + filters(specialtyId, appointmentType, siteId, professionalId)
                + " GROUP BY b.block_date ORDER BY b.block_date";
        return jdbc.query(sql, params,
                (rs, i) -> new DayCount(rs.getObject("block_date", LocalDate.class), rs.getInt("offers")));
    }

    private static MapSqlParameterSource params(Integer specialtyId, String appointmentType, Integer siteId,
            Long professionalId,
            LocalDateTime now) {
        return new MapSqlParameterSource("specialty", specialtyId)
                .addValue("type", appointmentType)
                .addValue("site", siteId)
                .addValue("professional", professionalId)
                .addValue("today", now.toLocalDate())
                .addValue("nowTime", now.toLocalTime().withNano(0));
    }

    private static String filters(Integer specialtyId, String appointmentType, Integer siteId,
            Long professionalId) {
        return (specialtyId == null ? "" : " AND ps.specialty_id = :specialty")
                + (appointmentType == null ? "" : " AND ty.code = :type")
                + (siteId == null ? "" : " AND b.site_id = :site")
                + (professionalId == null ? "" : " AND p.id = :professional");
    }
}
