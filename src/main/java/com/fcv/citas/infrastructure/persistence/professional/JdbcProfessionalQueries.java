package com.fcv.citas.infrastructure.persistence.professional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.professional.ProfessionalQueries;
import com.fcv.citas.application.shared.Refs.SiteRef;

/**
 * Lado de lectura de profesionales (D14): tres consultas (perfiles, especialidades y sedes)
 * sin N+1, agrupadas en memoria.
 */
@Component
class JdbcProfessionalQueries implements ProfessionalQueries {

    private static final String SELECT = """
            SELECT p.id, p.user_id, u.first_names, u.last_names, dt.code AS document_type, u.document_number,
                   u.email, u.phone, p.professional_code, p.license_number, p.active
            FROM professionals p
            JOIN users u ON u.id = p.user_id
            JOIN document_types dt ON dt.id = u.document_type_id
            """;

    private record Row(long id, long userId, String firstNames, String lastNames, String documentType,
            String documentNumber, String email, String phone, String code, String license, boolean active) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    JdbcProfessionalQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProfessionalView> list(Filter filter) {
        List<String> where = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter.active() != null) {
            where.add("p.active = :active");
            params.addValue("active", filter.active());
        }
        if (filter.specialtyId() != null) {
            where.add("EXISTS (SELECT 1 FROM professional_specialties ps"
                    + " WHERE ps.professional_id = p.id AND ps.specialty_id = :specialty)");
            params.addValue("specialty", filter.specialtyId());
        }
        if (filter.siteId() != null) {
            where.add("EXISTS (SELECT 1 FROM professional_sites s WHERE s.professional_id = p.id AND s.site_id = :site)");
            params.addValue("site", filter.siteId());
        }
        String sql = SELECT + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY u.last_names, u.first_names";
        return assemble(jdbc.query(sql, params, (rs, i) -> row(rs)));
    }

    @Override
    public Optional<ProfessionalView> findById(long id) {
        return assemble(jdbc.query(SELECT + " WHERE p.id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> row(rs))).stream().findFirst();
    }

    @Override
    public Optional<ProfessionalView> findByUserId(long userId) {
        return assemble(jdbc.query(SELECT + " WHERE p.user_id = :user", new MapSqlParameterSource("user", userId),
                (rs, i) -> row(rs))).stream().findFirst();
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getLong("user_id"), rs.getString("first_names"),
                rs.getString("last_names"), rs.getString("document_type"), rs.getString("document_number"),
                rs.getString("email"), rs.getString("phone"), rs.getString("professional_code"),
                rs.getString("license_number"), rs.getBoolean("active"));
    }

    private List<ProfessionalView> assemble(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource ids = new MapSqlParameterSource("ids", rows.stream().map(Row::id).toList());
        Map<Long, List<AssignedSpecialtyView>> specialties = jdbc.query("""
                SELECT ps.professional_id, s.id, s.code, s.name, t.code AS type, s.duration_minutes, s.active,
                       ps.is_primary
                FROM professional_specialties ps
                JOIN specialties s ON s.id = ps.specialty_id
                JOIN appointment_types t ON t.id = s.appointment_type_id
                WHERE ps.professional_id IN (:ids)
                ORDER BY ps.is_primary DESC, s.name
                """, ids, (rs, i) -> Map.entry(rs.getLong("professional_id"),
                new AssignedSpecialtyView(rs.getInt("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("type"), rs.getInt("duration_minutes"), rs.getBoolean("active"),
                        rs.getBoolean("is_primary"))))
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<Long, List<SiteRef>> sites = jdbc.query("""
                SELECT ps.professional_id, s.id, s.code, s.name
                FROM professional_sites ps
                JOIN sites s ON s.id = ps.site_id
                WHERE ps.professional_id IN (:ids)
                ORDER BY s.id
                """, ids, (rs, i) -> Map.entry(rs.getLong("professional_id"),
                new SiteRef(rs.getInt("id"), rs.getString("code"), rs.getString("name"))))
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return rows.stream().map(r -> new ProfessionalView(r.id(), r.userId(), r.firstNames(), r.lastNames(),
                r.documentType(), r.documentNumber(), r.email(), r.phone(), r.code(), r.license(), r.active(),
                specialties.getOrDefault(r.id(), List.of()), sites.getOrDefault(r.id(), List.of()))).toList();
    }
}
