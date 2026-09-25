package com.fcv.citas.infrastructure.persistence.eps;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.catalog.CatalogQueries.CatalogRef;
import com.fcv.citas.application.eps.EpsQueries;

/** Lado de lectura del CRUD de EPS con SQL plano (D14): incluye activas e inactivas. */
@Component
class JdbcEpsQueries implements EpsQueries {

    private static final String EPS = """
            SELECT e.id, e.code, e.name, e.active,
                   (SELECT COUNT(*) FROM eps_plans p WHERE p.eps_id = e.id) AS plan_count
            FROM eps e""";

    private static final String PLAN = """
            SELECT p.id, p.eps_id, p.code, p.name, p.active,
                   r.id AS regime_id, r.code AS regime_code, r.name AS regime_name
            FROM eps_plans p JOIN regimes r ON r.id = p.regime_id""";

    private final JdbcTemplate jdbc;

    JdbcEpsQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EpsView> findAll() {
        return jdbc.query(EPS + " ORDER BY e.name", (rs, i) -> eps(rs));
    }

    @Override
    public Optional<EpsView> findById(int id) {
        return jdbc.query(EPS + " WHERE e.id = ?", (rs, i) -> eps(rs), id).stream().findFirst();
    }

    @Override
    public List<EpsPlanView> plansOf(int epsId) {
        return jdbc.query(PLAN + " WHERE p.eps_id = ? ORDER BY p.name", (rs, i) -> plan(rs), epsId);
    }

    @Override
    public Optional<EpsPlanView> findPlan(int id) {
        return jdbc.query(PLAN + " WHERE p.id = ?", (rs, i) -> plan(rs), id).stream().findFirst();
    }

    private static EpsView eps(ResultSet rs) throws SQLException {
        return new EpsView(rs.getInt("id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                rs.getInt("plan_count"));
    }

    private static EpsPlanView plan(ResultSet rs) throws SQLException {
        return new EpsPlanView(rs.getInt("id"), rs.getInt("eps_id"), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("active"),
                new CatalogRef(rs.getInt("regime_id"), rs.getString("regime_code"), rs.getString("regime_name")));
    }
}
