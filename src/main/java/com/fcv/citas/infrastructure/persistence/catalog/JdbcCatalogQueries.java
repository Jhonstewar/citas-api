package com.fcv.citas.infrastructure.persistence.catalog;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.catalog.CatalogQueries;
import com.fcv.citas.domain.affiliation.InsurancePlanCatalog;
import com.fcv.citas.domain.eps.RegimeCatalog;

/**
 * Lectura de catalogos fijos con SQL plano: son tablas de solo lectura sin comportamiento y un
 * mapeo JPA por cada una no aportaria nada. Es el lado de lectura (decision D14); las escrituras
 * de agregados siguen pasando por JPA.
 */
@Component
class JdbcCatalogQueries implements CatalogQueries, InsurancePlanCatalog, RegimeCatalog {

    /**
     * Un plan solo se ofrece y solo se acepta si esta activo y su EPS tambien (RF-06). El
     * predicado se escribe una vez para que la lista publica y la validacion del registro no
     * puedan divergir (HU-009).
     */
    private static final String SELECTABLE_PLAN = """
            FROM eps_plans p
            JOIN eps e ON e.id = p.eps_id
            JOIN regimes r ON r.id = p.regime_id
            WHERE p.active AND e.active""";

    private final JdbcTemplate jdbc;

    JdbcCatalogQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SiteView> sites() {
        return jdbc.query("SELECT id, code, name, address, city, department FROM sites WHERE active ORDER BY id",
                (rs, i) -> new SiteView(rs.getInt("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("address"), rs.getString("city"), rs.getString("department")));
    }

    @Override
    public List<AppointmentTypeView> appointmentTypes() {
        return jdbc.query("SELECT code, name, requires_admin_approval FROM appointment_types ORDER BY id",
                (rs, i) -> new AppointmentTypeView(rs.getString("code"), rs.getString("name"),
                        rs.getBoolean("requires_admin_approval")));
    }

    @Override
    public List<StatusView> appointmentStatuses() {
        return jdbc.query("SELECT code, name, is_terminal FROM appointment_statuses ORDER BY id",
                (rs, i) -> new StatusView(rs.getString("code"), rs.getString("name"), rs.getBoolean("is_terminal")));
    }

    @Override
    public List<StatusView> rescheduleStatuses() {
        return jdbc.query("SELECT code, name, is_terminal FROM reschedule_statuses ORDER BY id",
                (rs, i) -> new StatusView(rs.getString("code"), rs.getString("name"), rs.getBoolean("is_terminal")));
    }

    @Override
    public List<CodeName> documentTypes() {
        return codeNames("SELECT code, name FROM document_types WHERE active ORDER BY id");
    }

    @Override
    public List<CodeName> roles() {
        return codeNames("SELECT code, name FROM roles ORDER BY id");
    }

    @Override
    public List<CodeName> regimes() {
        return codeNames("SELECT code, name FROM regimes ORDER BY id");
    }

    @Override
    public List<InsurancePlanView> insurancePlans() {
        return jdbc.query("""
                SELECT p.id AS plan_id, p.code AS plan_code, p.name AS plan_name,
                       e.id AS eps_id, e.code AS eps_code, e.name AS eps_name,
                       r.id AS regime_id, r.code AS regime_code, r.name AS regime_name
                """ + SELECTABLE_PLAN + " ORDER BY e.name, p.name",
                (rs, i) -> new InsurancePlanView(rs.getInt("plan_id"), rs.getString("plan_code"),
                        rs.getString("plan_name"),
                        new CatalogRef(rs.getInt("eps_id"), rs.getString("eps_code"), rs.getString("eps_name")),
                        new CatalogRef(rs.getInt("regime_id"), rs.getString("regime_code"),
                                rs.getString("regime_name"))));
    }

    @Override
    public boolean isSelectable(int epsPlanId) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) " + SELECTABLE_PLAN + " AND p.id = ?",
                Integer.class, epsPlanId);
        return found != null && found > 0;
    }

    /** HU-012 CA-02: el regimen de un plan debe estar en el catalogo fijo (V4), el mismo que lista {@link #regimes}. */
    @Override
    public boolean exists(String code) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM regimes WHERE code = ?", Integer.class, code);
        return found != null && found > 0;
    }

    private List<CodeName> codeNames(String sql) {
        return jdbc.query(sql, (rs, i) -> new CodeName(rs.getString("code"), rs.getString("name")));
    }
}
