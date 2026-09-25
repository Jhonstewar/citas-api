package com.fcv.citas.support;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * EPS, planes y afiliaciones de prueba escritos con SQL directo (no pasan por la API bajo prueba).
 * Todo lleva el prefijo {@code IT_EPS_} en el codigo (y en el nombre) y {@link #cleanUp()} lo borra
 * en orden de dependencias. Los usuarios los crea {@link S3TestData}; su limpieza borra sus
 * afiliaciones por {@code ON DELETE CASCADE}.
 */
public final class EpsTestData {

    public static final String PREFIX = "IT_EPS_";

    private final JdbcTemplate jdbc;

    public EpsTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Codigo unico con el prefijo, de a lo sumo 20 caracteres (limite de {@code eps.code}). */
    public static String code() {
        return PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String name(String label) {
        return "IT_EPS " + label + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    public int eps(String label, boolean active) {
        String code = code();
        jdbc.update("INSERT INTO eps (code, name, active) VALUES (?, ?, ?)", code, name(label), active);
        return jdbc.queryForObject("SELECT id FROM eps WHERE code = ?", Integer.class, code);
    }

    public int plan(int epsId, String regimeCode, String label, boolean active) {
        String code = code();
        jdbc.update("""
                INSERT INTO eps_plans (eps_id, regime_id, code, name, active)
                VALUES (?, (SELECT id FROM regimes WHERE code = ?), ?, ?, ?)
                """, epsId, regimeCode, code, name(label), active);
        return jdbc.queryForObject("SELECT id FROM eps_plans WHERE eps_id = ? AND code = ?", Integer.class, epsId,
                code);
    }

    /** Afiliacion sembrada; {@code endedOn} nulo = vigente. */
    public long affiliation(long userId, int planId, LocalDate startedOn, LocalDate endedOn) {
        jdbc.update("INSERT INTO affiliations (user_id, eps_plan_id, is_current, started_on, ended_on)"
                + " VALUES (?, ?, ?, ?, ?)", userId, planId, endedOn == null, startedOn, endedOn);
        return jdbc.queryForObject("SELECT MAX(id) FROM affiliations WHERE user_id = ? AND eps_plan_id = ?",
                Long.class, userId, planId);
    }

    public int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** Borra planes y EPS de prueba, y las afiliaciones que aun los referencien. */
    public void cleanUp() {
        String plans = "SELECT p.id FROM eps_plans p JOIN eps e ON e.id = p.eps_id WHERE e.code LIKE '" + PREFIX
                + "%' OR p.code LIKE '" + PREFIX + "%'";
        jdbc.update("DELETE FROM affiliations WHERE eps_plan_id IN (SELECT id FROM (" + plans + ") x)");
        jdbc.update("DELETE FROM eps_plans WHERE id IN (SELECT id FROM (" + plans + ") x)");
        jdbc.update("DELETE FROM eps WHERE code LIKE ?", PREFIX + "%");
    }
}
