package com.fcv.citas.infrastructure.persistence.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.schedule.ScheduleQueries;
import com.fcv.citas.application.shared.Refs.SiteRef;

/**
 * Lectura del calendario (D14): una consulta de bloques con sus slots y si cada slot esta libre
 * (sin fila en {@code slot_reservations}, sea cita o retencion).
 */
@Component
class JdbcScheduleQueries implements ScheduleQueries {

    private static final String SELECT = """
            SELECT b.id AS block_id, b.block_date, b.start_time AS block_start, b.end_time AS block_end,
                   si.id AS site_id, si.code AS site_code, si.name AS site_name,
                   s.id AS slot_id, s.start_time AS slot_start, s.end_time AS slot_end,
                   (r.slot_id IS NULL) AS available
            FROM availability_blocks b
            JOIN sites si ON si.id = b.site_id
            LEFT JOIN availability_slots s ON s.availability_block_id = b.id
            LEFT JOIN slot_reservations r ON r.slot_id = s.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcScheduleQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BlockView> calendar(long professionalId, LocalDate from, LocalDate to) {
        return query(SELECT + " WHERE b.professional_id = :professional AND b.block_date BETWEEN :from AND :to"
                + " ORDER BY b.block_date, b.start_time, s.start_time",
                new MapSqlParameterSource("professional", professionalId).addValue("from", from).addValue("to", to));
    }

    @Override
    public Optional<BlockView> findBlock(long blockId) {
        return query(SELECT + " WHERE b.id = :id ORDER BY s.start_time", new MapSqlParameterSource("id", blockId))
                .stream().findFirst();
    }

    private record Head(long id, LocalDate date, LocalTime start, LocalTime end, SiteRef site) {
    }

    private List<BlockView> query(String sql, MapSqlParameterSource params) {
        Map<Head, List<SlotView>> grouped = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            Head head = new Head(rs.getLong("block_id"), rs.getObject("block_date", LocalDate.class),
                    rs.getObject("block_start", LocalTime.class), rs.getObject("block_end", LocalTime.class),
                    new SiteRef(rs.getInt("site_id"), rs.getString("site_code"), rs.getString("site_name")));
            List<SlotView> slots = grouped.computeIfAbsent(head, h -> new ArrayList<>());
            long slotId = rs.getLong("slot_id");
            if (!rs.wasNull()) {
                slots.add(new SlotView(slotId, rs.getObject("slot_start", LocalTime.class),
                        rs.getObject("slot_end", LocalTime.class), rs.getBoolean("available")));
            }
        });
        return grouped.entrySet().stream().map(e -> new BlockView(e.getKey().id(), e.getKey().date(),
                e.getKey().start(), e.getKey().end(), e.getKey().site(),
                e.getValue().stream().anyMatch(s -> !s.available()), List.copyOf(e.getValue()))).toList();
    }
}
