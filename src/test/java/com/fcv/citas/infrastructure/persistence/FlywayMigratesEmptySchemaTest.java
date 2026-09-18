package com.fcv.citas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * HU-001 CA-08 — las migraciones levantan el esquema completo partiendo de una base VACIA.
 *
 * <p>La verificacion independiente de S2 marco este criterio como NO VERIFICABLE: el resto de las
 * pruebas corren contra una base ya migrada, asi que ninguna demostraba que V1..V4 funcionen
 * desde cero. Esta prueba crea su propio esquema desechable, lo migra y lo destruye, sin tocar ni
 * la base de desarrollo ni la de pruebas.</p>
 *
 * <p>El esquema encaja en el patron {@code citas_fcv_%} sobre el que
 * {@code scripts/init-test-db.ps1} concede privilegios al usuario de la aplicacion.</p>
 */
class FlywayMigratesEmptySchemaTest {

    /** Esquema desechable: se crea al empezar y se destruye al terminar. */
    private static final String SCHEMA = "citas_fcv_migrations_check";

    /** Tablas de negocio de V1..V3, sin contar el historial de Flyway. */
    private static final int EXPECTED_TABLES = 24;

    /** Catalogos fijos sembrados por V4 (RF-11). */
    private static final List<String> SEEDED_CATALOGS = List.of(
            "appointment_statuses", "appointment_types", "document_types", "regimes",
            "reschedule_statuses", "roles", "sites");

    private static String user;
    private static String password;
    private static String serverUrl;
    private static String schemaUrl;
    private static MigrateResult result;
    private static Flyway flyway;

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeAll
    static void migrateFromEmptySchema() throws SQLException {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "3306");
        user = env("DB_USER", "citas_app");
        password = env("DB_PASSWORD", "");

        String options = "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                + "&connectionTimeZone=America/Bogota&forceConnectionTimeZoneToSession=true";
        serverUrl = "jdbc:mysql://%s:%s/%s".formatted(host, port, options);
        schemaUrl = "jdbc:mysql://%s:%s/%s%s".formatted(host, port, SCHEMA, options);

        // SCHEMA es una constante del codigo, no una entrada: no hay interpolacion de datos.
        try (Connection connection = DriverManager.getConnection(serverUrl, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + SCHEMA + "`");
            statement.execute("CREATE DATABASE `" + SCHEMA + "` "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        flyway = Flyway.configure()
                .dataSource(schemaUrl, user, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
        result = flyway.migrate();
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
    void aplicaLasCuatroMigracionesEnOrden() throws SQLException {
        assertThat(result.migrationsExecuted).isEqualTo(4);

        // Se lee el historial con SQL plano en vez de la API de Flyway: lo que importa es lo que
        // quedo registrado en la base, no lo que el objeto de resultado dice haber hecho.
        List<String> versions = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT version, success FROM flyway_schema_history "
                                + "WHERE version IS NOT NULL ORDER BY installed_rank")) {
            while (rows.next()) {
                assertThat(rows.getBoolean("success"))
                        .as("migracion %s marcada como fallida", rows.getString("version"))
                        .isTrue();
                versions.add(rows.getString("version"));
            }
        }
        assertThat(versions).containsExactly("1", "2", "3", "4");
    }

    @Test
    void dejaElModeloCompletoDe24Tablas() throws SQLException {
        assertThat(countTables()).isEqualTo(EXPECTED_TABLES);
    }

    @Test
    void elEsquemaMigradoValidaContraLasMigraciones() {
        // Detecta un checksum alterado o una migracion aplicada que ya no existe en el classpath.
        assertThatCode(() -> flyway.validate()).doesNotThrowAnyException();
    }

    @Test
    void siembraLosCatalogosFijos() throws SQLException {
        for (String table : SEEDED_CATALOGS) {
            assertThat(countRows(table))
                    .as("el catalogo fijo %s quedo vacio tras V4", table)
                    .isPositive();
        }
    }

    private static int countTables() throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = '" + SCHEMA + "' "
                                + "AND table_name <> 'flyway_schema_history'")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static int countRows(String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
