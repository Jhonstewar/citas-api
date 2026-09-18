package com.fcv.citas.support;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * Datos de prueba de S3 escritos con SQL directo (no pasan por la API bajo prueba). Todo lo que
 * crea lleva el dominio {@code @s3.fcv.test} o el prefijo {@code IT_S3_} y {@link #cleanUp()} lo
 * borra en orden de dependencias. El historial ya no cae en cascada (V5): se borra primero.
 */
public final class S3TestData {

    public static final String DOMAIN = "@s3.fcv.test";
    public static final String SPECIALTY_PREFIX = "IT_S3_";
    /** Hash BCrypt real de "Clave-Prueba#2026", para poder iniciar sesion con usuarios sembrados. */
    public static final String PASSWORD = "Clave-Prueba#2026";

    private final JdbcTemplate jdbc;
    private final String passwordHash;

    public S3TestData(JdbcTemplate jdbc, String passwordHash) {
        this.jdbc = jdbc;
        this.passwordHash = passwordHash;
    }

    public static String email(String label) {
        return label + "-" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
    }

    public static String document() {
        return "S3" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }

    public static String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Crea un usuario con un rol y devuelve su id. */
    public long user(String label, String role) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO users (document_type_id, document_number, first_names, last_names, email, phone,
                                       password_hash, active)
                    VALUES ((SELECT id FROM document_types WHERE code = 'CC'), ?, ?, 'Prueba', ?, '3001234567', ?, TRUE)
                    """, new String[] { "id" });
            ps.setString(1, document());
            ps.setString(2, label);
            ps.setString(3, email(label));
            ps.setString(4, passwordHash);
            return ps;
        }, key);
        long id = key.getKey().longValue();
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = ?", id, role);
        return id;
    }

    public int specialty(String type, int durationMinutes) {
        String code = uniqueCode(SPECIALTY_PREFIX);
        jdbc.update("""
                INSERT INTO specialties (appointment_type_id, code, name, duration_minutes, active)
                VALUES ((SELECT id FROM appointment_types WHERE code = ?), ?, ?, ?, TRUE)
                """, type, code, "Especialidad " + code, durationMinutes);
        return jdbc.queryForObject("SELECT id FROM specialties WHERE code = ?", Integer.class, code);
    }

    public int generalMedicineId() {
        return jdbc.queryForObject("SELECT id FROM specialties WHERE code = 'MEDICINA_GENERAL'", Integer.class);
    }

    public int siteId(String code) {
        return jdbc.queryForObject("SELECT id FROM sites WHERE code = ?", Integer.class, code);
    }

    /** Profesional activo con su usuario, las especialidades dadas (la primera es primaria) y sedes. */
    public Professional professional(String label, int[] specialtyIds, int... siteIds) {
        long userId = user(label, "PROFESSIONAL");
        String code = uniqueCode("P");
        jdbc.update("INSERT INTO professionals (user_id, professional_code, license_number, active) VALUES (?, ?, ?, TRUE)",
                userId, code, "LIC-" + code);
        long id = jdbc.queryForObject("SELECT id FROM professionals WHERE user_id = ?", Long.class, userId);
        for (int i = 0; i < specialtyIds.length; i++) {
            jdbc.update("INSERT INTO professional_specialties (professional_id, specialty_id, is_primary) VALUES (?, ?, ?)",
                    id, specialtyIds[i], i == 0);
        }
        for (int site : siteIds) {
            jdbc.update("INSERT INTO professional_sites (professional_id, site_id) VALUES (?, ?)", id, site);
        }
        return new Professional(id, userId);
    }

    public record Professional(long id, long userId) {
    }

    /** Borra todo lo creado por las pruebas de S3, respetando las FK (sin cascada en el historial). */
    public void cleanUp() {
        String users = "SELECT id FROM users WHERE email LIKE '%" + DOMAIN + "'";
        String professionals = "SELECT id FROM professionals WHERE user_id IN (" + users + ")";
        String appointments = "SELECT id FROM appointments WHERE patient_user_id IN (" + users + ")"
                + " OR professional_id IN (" + professionals + ")";
        jdbc.update("DELETE FROM appointment_status_history WHERE appointment_id IN (SELECT id FROM (" + appointments
                + ") a)");
        jdbc.update("DELETE FROM slot_reservations WHERE appointment_id IN (SELECT id FROM (" + appointments + ") a)");
        jdbc.update("DELETE FROM appointments WHERE id IN (SELECT id FROM (" + appointments + ") a)");
        jdbc.update("DELETE FROM availability_blocks WHERE professional_id IN (" + professionals + ")");
        jdbc.update("DELETE FROM professionals WHERE user_id IN (" + users + ")");
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%" + DOMAIN);
        jdbc.update("DELETE FROM specialties WHERE code LIKE ?", SPECIALTY_PREFIX + "%");
    }
}
