package com.fcv.citas.infrastructure.persistence.affiliation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.affiliation.AffiliationQueries;
import com.fcv.citas.application.catalog.CatalogQueries.CatalogRef;
import com.fcv.citas.application.catalog.CatalogQueries.InsurancePlanView;

/**
 * Lectura de la afiliacion con SQL plano (D14). El plan sale con el mismo cuerpo que el catalogo
 * publico, SIN filtrar por activo: una afiliacion declarada sigue mostrando su EPS y su plan aunque
 * luego se desactiven (HU-012 CA-05).
 */
@Component
class JdbcAffiliationQueries implements AffiliationQueries {

    private static final String SELECT = """
            SELECT a.id, a.started_on,
                   p.id AS plan_id, p.code AS plan_code, p.name AS plan_name,
                   e.id AS eps_id, e.code AS eps_code, e.name AS eps_name,
                   r.id AS regime_id, r.code AS regime_code, r.name AS regime_name
            FROM affiliations a
            JOIN eps_plans p ON p.id = a.eps_plan_id
            JOIN eps e ON e.id = p.eps_id
            JOIN regimes r ON r.id = p.regime_id""";

    private final JdbcTemplate jdbc;

    JdbcAffiliationQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AffiliationView> current(long userId) {
        return jdbc.query(SELECT + " WHERE a.user_id = ? AND a.is_current", (rs, i) -> map(rs), userId).stream()
                .findFirst();
    }

    @Override
    public Optional<AffiliationView> findById(long affiliationId) {
        return jdbc.query(SELECT + " WHERE a.id = ?", (rs, i) -> map(rs), affiliationId).stream().findFirst();
    }

    private static AffiliationView map(ResultSet rs) throws SQLException {
        return new AffiliationView(rs.getLong("id"),
                new InsurancePlanView(rs.getInt("plan_id"), rs.getString("plan_code"), rs.getString("plan_name"),
                        new CatalogRef(rs.getInt("eps_id"), rs.getString("eps_code"), rs.getString("eps_name")),
                        new CatalogRef(rs.getInt("regime_id"), rs.getString("regime_code"),
                                rs.getString("regime_name"))),
                rs.getObject("started_on", LocalDate.class));
    }
}
