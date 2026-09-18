package com.fcv.citas.infrastructure.persistence.catalog;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.catalog.CatalogQueries;

/**
 * Lectura de catalogos fijos con SQL plano: son tablas de solo lectura sin comportamiento y un
 * mapeo JPA por cada una no aportaria nada. Es el lado de lectura (decision D14); las escrituras
 * de agregados siguen pasando por JPA.
 */
@Component
class JdbcCatalogQueries implements CatalogQueries {

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

    private List<CodeName> codeNames(String sql) {
        return jdbc.query(sql, (rs, i) -> new CodeName(rs.getString("code"), rs.getString("name")));
    }
}
