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
 * pruebas corren contra una base ya migrada, asi que ninguna demostraba que V1..Vn funcionen
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
    void aplicaTodasLasMigracionesEnOrden() throws SQLException {
        assertThat(result.migrationsExecuted).isEqualTo(10);

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
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
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

    /** HU-010 CA-02 (V7): las dos sedes con la direccion literal del PRD §3. */
    @Test
    void siembraLasDosSedesConSuDireccion() throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT code, address FROM sites ORDER BY id")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("code")).isEqualTo("HIC");
            assertThat(rows.getString("address")).isEqualTo("Km 7 Autopista Bucaramanga–Piedecuesta, Valle de Menzulí");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("code")).isEqualTo("ICV");
            assertThat(rows.getString("address")).isEqualTo("Calle 155A No. 23-58, Urbanización El Bosque");
            assertThat(rows.next()).isFalse();
        }
    }

    /** V6 (decision D7): Medicina General existe desde la migracion, general y de 30 minutos. */
    @Test
    void siembraMedicinaGeneral() throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT s.duration_minutes, t.code FROM specialties s "
                                + "JOIN appointment_types t ON t.id = s.appointment_type_id "
                                + "WHERE s.code = 'MEDICINA_GENERAL' AND s.active")) {
            assertThat(rows.next()).as("falta la especialidad MEDICINA_GENERAL").isTrue();
            assertThat(rows.getInt(1)).isEqualTo(30);
            assertThat(rows.getString(2)).isEqualTo("GENERAL");
        }
    }

    /** V5 (decision D8): el historial ya no se borra en cascada y admite el origen PROFESSIONAL. */
    @Test
    void elHistorialEsAppendOnlyYAdmiteOrigenProfesional() throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS "
                            + "WHERE CONSTRAINT_SCHEMA = '" + SCHEMA + "' "
                            + "AND CONSTRAINT_NAME = 'fk_ash_appointment'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isIn("RESTRICT", "NO ACTION");
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' "
                            + "AND TABLE_NAME = 'appointment_status_history' AND COLUMN_NAME = 'source'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).contains("'PROFESSIONAL'");
            }
        }
    }

    /**
     * V8 (R1, HU-011): el nombre de especialidad es unico en la BD, con una collation insensible a
     * mayusculas y tildes, igual que la comprobacion del caso de uso.
     */
    @Test
    void elNombreDeEspecialidadEsUnicoSinDistinguirMayusculasNiTildes() throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(
                    "SELECT GROUP_CONCAT(COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE "
                            + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'specialties' "
                            + "AND CONSTRAINT_NAME = 'uq_specialties_name'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).as("falta la unica uq_specialties_name").isEqualTo("name");
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COLLATION_NAME FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' "
                            + "AND TABLE_NAME = 'specialties' AND COLUMN_NAME = 'name'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("utf8mb4_0900_ai_ci");
            }
        }
    }

    /**
     * V9 (D33, HU-012 DoD): nombre de EPS unico y nombre de plan unico dentro de su EPS, con la
     * collation insensible a mayusculas y tildes que usa la comprobacion del caso de uso.
     */
    @Test
    void losNombresDeEpsYDePlanSonUnicos() throws SQLException {
        assertThat(uniqueColumns("eps", "uq_eps_name")).isEqualTo("name");
        assertThat(uniqueColumns("eps_plans", "uq_eps_plans_eps_name")).isEqualTo("eps_id,name");
        assertThat(collation("eps", "name")).isEqualTo("utf8mb4_0900_ai_ci");
        assertThat(collation("eps_plans", "name")).isEqualTo("utf8mb4_0900_ai_ci");
    }

    /**
     * V9 (D32, HU-009 CA-04 y CA-09): la unica por usuario y plan pasa a incluir la vigencia, para
     * poder volver a un plan ya usado; la de una sola afiliacion vigente por usuario sigue en pie.
     */
    @Test
    void laUnicaDeAfiliacionPermiteVolverAUnPlanYaUsado() throws SQLException {
        assertThat(uniqueColumns("affiliations", "uq_affiliations_user_plan")).isNull();
        assertThat(uniqueColumns("affiliations", "uq_affiliations_user_plan_current"))
                .isEqualTo("user_id,eps_plan_id,current_marker");
        assertThat(uniqueColumns("affiliations", "uq_affiliations_user_current"))
                .isEqualTo("user_id,current_marker");
    }

    /**
     * V10 (D31, HU-027 CA-03/CA-09): la solicitud de reprogramacion guarda la franja ANTERIOR (fecha,
     * horas y sede) y la sede propuesta, obligatorias, con FK a {@code sites}.
     */
    @Test
    void laReprogramacionGuardaLaFranjaAnteriorYLaSedePropuesta() throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement()) {
            for (String column : List.of("previous_date", "previous_start_time", "previous_end_time",
                    "previous_site_id", "proposed_site_id")) {
                try (ResultSet rows = statement.executeQuery("SELECT IS_NULLABLE FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'reschedule_requests'"
                        + " AND COLUMN_NAME = '" + column + "'")) {
                    assertThat(rows.next()).as("falta la columna %s", column).isTrue();
                    assertThat(rows.getString(1)).as("%s obligatoria", column).isEqualTo("NO");
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE"
                    + " WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'reschedule_requests'"
                    + " AND REFERENCED_TABLE_NAME = 'sites'"
                    + " AND COLUMN_NAME IN ('previous_site_id', 'proposed_site_id')")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }

    private static String uniqueColumns(String table, String index) throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS "
                                + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = '" + table + "' "
                                + "AND INDEX_NAME = '" + index + "' AND NON_UNIQUE = 0")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static String collation(String table, String column) throws SQLException {
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + SCHEMA
                                + "' AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'")) {
            rows.next();
            return rows.getString(1);
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
