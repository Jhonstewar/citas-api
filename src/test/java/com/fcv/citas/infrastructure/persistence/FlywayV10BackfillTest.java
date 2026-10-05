package com.fcv.citas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * HU-027 DoD "migracion sin perdida de datos" — V10 sobre filas previas de {@code reschedule_requests}.
 *
 * <p>Crea un esquema desechable, migra hasta V9, inserta solicitudes con el esquema anterior (sin
 * franja previa ni sede propuesta), migra a V10 y comprueba el backfill y que ninguna fila se pierde.
 * Igual que {@link FlywayMigratesEmptySchemaTest}, usa su propia instancia de Flyway y un esquema que
 * encaja en {@code citas_fcv_%}; no toca la base de desarrollo ni la de pruebas.</p>
 */
class FlywayV10BackfillTest {

    private static final String SCHEMA = "citas_fcv_migrations_v10_check";

    private static String user;
    private static String password;
    private static String serverUrl;
    private static String schemaUrl;

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Connection schema() throws SQLException {
        return DriverManager.getConnection(schemaUrl, user, password);
    }

    @BeforeAll
    static void migrateToV9SeedAndMigrateToV10() throws SQLException {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        user = env("DB_USER", "citas_app");
        password = env("DB_PASSWORD", "");
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                + "&connectionTimeZone=America/Bogota&forceConnectionTimeZoneToSession=true";
        serverUrl = "jdbc:mysql://%s:%s/%s".formatted(host, port, options);
        schemaUrl = "jdbc:mysql://%s:%s/%s%s".formatted(host, port, SCHEMA, options);

        try (Connection connection = DriverManager.getConnection(serverUrl, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + SCHEMA + "`");
            statement.execute("CREATE DATABASE `" + SCHEMA + "` "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        Flyway.configure().dataSource(schemaUrl, user, password)
                .locations("classpath:db/migration").target("9").cleanDisabled(true).load().migrate();

        seedRequestsWithTheV9Schema();

        Flyway.configure().dataSource(schemaUrl, user, password)
                .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
    }

    /**
     * Dos solicitudes: la 1 PENDING con un slot retenido en un bloque de ICV (la cita esta en HIC), y la
     * 2 ya decidida y sin slots retenidos. Esquema V9: sin columnas de franja previa ni sede propuesta.
     */
    private static void seedRequestsWithTheV9Schema() throws SQLException {
        try (Connection connection = schema(); Statement s = connection.createStatement()) {
            s.execute("INSERT INTO users (document_type_id, document_number, first_names, last_names, email,"
                    + " password_hash) VALUES ((SELECT id FROM document_types WHERE code = 'CC'), '9100001',"
                    + " 'Paciente', 'Migracion', 'v10-patient@example.test', 'x'),"
                    + " ((SELECT id FROM document_types WHERE code = 'CC'), '9100002',"
                    + " 'Profesional', 'Migracion', 'v10-pro@example.test', 'x')");
            s.execute("INSERT INTO professionals (user_id, professional_code, license_number) VALUES"
                    + " ((SELECT id FROM users WHERE email = 'v10-pro@example.test'), 'V10PRO', 'V10LIC')");

            // Dos citas APPROVED en HIC.
            for (String[] appt : new String[][] { { "2030-03-04", "08:00:00", "08:30:00" },
                    { "2030-03-05", "09:00:00", "09:30:00" } }) {
                s.execute("INSERT INTO appointments (patient_user_id, professional_id, site_id, specialty_id,"
                        + " status_id, scheduled_date, start_time, end_time) VALUES ("
                        + "(SELECT id FROM users WHERE email = 'v10-patient@example.test'),"
                        + "(SELECT id FROM professionals WHERE professional_code = 'V10PRO'),"
                        + "(SELECT id FROM sites WHERE code = 'HIC'),"
                        + "(SELECT id FROM specialties WHERE code = 'MEDICINA_GENERAL'),"
                        + "(SELECT id FROM appointment_statuses WHERE code = 'APPROVED'),"
                        + "'" + appt[0] + "', '" + appt[1] + "', '" + appt[2] + "')");
            }

            // Solicitud 1: PENDING, sin decidir; la franja pedida esta en un bloque de ICV.
            s.execute("INSERT INTO reschedule_requests (appointment_id, status_id, requested_by_user_id,"
                    + " proposed_date, proposed_start_time, proposed_end_time) VALUES ("
                    + "(SELECT id FROM appointments WHERE scheduled_date = '2030-03-04'),"
                    + "(SELECT id FROM reschedule_statuses WHERE code = 'PENDING'),"
                    + "(SELECT id FROM users WHERE email = 'v10-patient@example.test'),"
                    + "'2030-03-10', '10:00:00', '10:30:00')");
            s.execute("INSERT INTO availability_blocks (professional_id, site_id, block_date, start_time, end_time)"
                    + " VALUES ((SELECT id FROM professionals WHERE professional_code = 'V10PRO'),"
                    + " (SELECT id FROM sites WHERE code = 'ICV'), '2030-03-10', '10:00:00', '11:00:00')");
            s.execute("INSERT INTO availability_slots (availability_block_id, start_time) VALUES"
                    + " ((SELECT id FROM availability_blocks WHERE block_date = '2030-03-10'), '10:00:00')");
            s.execute("INSERT INTO slot_reservations (slot_id, reservation_type, reschedule_request_id, slot_order)"
                    + " VALUES ((SELECT id FROM availability_slots LIMIT 1), 'RESCHEDULE_REQUEST',"
                    + " (SELECT id FROM reschedule_requests WHERE proposed_date = '2030-03-10'), 1)");

            // Solicitud 2: decidida (APPROVED), ya sin slots retenidos.
            s.execute("INSERT INTO reschedule_requests (appointment_id, status_id, requested_by_user_id,"
                    + " proposed_date, proposed_start_time, proposed_end_time, decided_by_user_id, decided_at)"
                    + " VALUES ((SELECT id FROM appointments WHERE scheduled_date = '2030-03-05'),"
                    + "(SELECT id FROM reschedule_statuses WHERE code = 'APPROVED'),"
                    + "(SELECT id FROM users WHERE email = 'v10-patient@example.test'),"
                    + "'2030-03-12', '14:00:00', '14:30:00',"
                    + "(SELECT id FROM users WHERE email = 'v10-pro@example.test'), NOW(6))");
        }
    }

    @AfterAll
    static void dropThrowawaySchema() throws SQLException {
        if (serverUrl == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(serverUrl, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + SCHEMA + "`");
        }
    }

    @Test
    void ningunaSolicitudPreviaSePierde() throws SQLException {
        try (Connection connection = schema(); Statement s = connection.createStatement();
                ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM reschedule_requests")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void laSolicitudPendienteRecibeLaFranjaActualDeLaCitaYLaSedeDeSuSlotRetenido() throws SQLException {
        try (Connection connection = schema(); Statement s = connection.createStatement();
                ResultSet rows = s.executeQuery("SELECT r.previous_date, r.previous_start_time,"
                        + " r.previous_end_time, ps.code AS previous_site, xs.code AS proposed_site,"
                        + " r.proposed_date, r.proposed_start_time"
                        + " FROM reschedule_requests r JOIN sites ps ON ps.id = r.previous_site_id"
                        + " JOIN sites xs ON xs.id = r.proposed_site_id WHERE r.proposed_date = '2030-03-10'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("previous_date")).isEqualTo("2030-03-04");
            assertThat(rows.getString("previous_start_time")).isEqualTo("08:00:00");
            assertThat(rows.getString("previous_end_time")).isEqualTo("08:30:00");
            assertThat(rows.getString("previous_site")).isEqualTo("HIC");
            assertThat(rows.getString("proposed_site")).as("sede del bloque del slot retenido").isEqualTo("ICV");
            // Los datos propuestos originales no se tocan.
            assertThat(rows.getString("proposed_start_time")).isEqualTo("10:00:00");
        }
    }

    @Test
    void laSolicitudDecididaSinSlotsRecibeLaSedeDeLaCita() throws SQLException {
        try (Connection connection = schema(); Statement s = connection.createStatement();
                ResultSet rows = s.executeQuery("SELECT r.previous_date, r.previous_start_time,"
                        + " r.previous_end_time, ps.code AS previous_site, xs.code AS proposed_site"
                        + " FROM reschedule_requests r JOIN sites ps ON ps.id = r.previous_site_id"
                        + " JOIN sites xs ON xs.id = r.proposed_site_id WHERE r.proposed_date = '2030-03-12'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("previous_date")).isEqualTo("2030-03-05");
            assertThat(rows.getString("previous_start_time")).isEqualTo("09:00:00");
            assertThat(rows.getString("previous_end_time")).isEqualTo("09:30:00");
            assertThat(rows.getString("previous_site")).isEqualTo("HIC");
            assertThat(rows.getString("proposed_site")).as("sede de la cita, sin slots retenidos")
                    .isEqualTo("HIC");
        }
    }

    @Test
    void trasElBackfillLasColumnasNuevasSonObligatoriasYNoQuedaNingunNulo() throws SQLException {
        try (Connection connection = schema(); Statement s = connection.createStatement();
                ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM reschedule_requests WHERE previous_date IS NULL"
                        + " OR previous_start_time IS NULL OR previous_end_time IS NULL"
                        + " OR previous_site_id IS NULL OR proposed_site_id IS NULL")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
        }
    }
}
