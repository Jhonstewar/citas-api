package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-034 CA-11: {@code GET /api/automation/appointments/daily} (resumen operativo diario, WF-003).
 * Sin PII; los pendientes son los de {@code summary(today)}. Reloj fijo: 2032-06-15 10:00 en Bogota.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutomationDailyIntegrationTest {

    private static final String KEY = "test-only-automation-key-0123456789abcdef";
    private static final String KEY_HEADER = "X-Automation-Key";
    private static final String URL = "/api/automation/appointments/daily";
    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final LocalDateTime NOW = LocalDateTime.of(2032, 6, 15, 10, 0);
    private static final LocalDate DAY = NOW.toLocalDate();
    private static final LocalDate EMPTY_DAY = LocalDate.of(2032, 7, 20);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            // 2032-06-15 23:30 en Bogota = 2032-06-16 04:30 UTC: "hoy" debe ser el 15 (Bogota), no el 16.
            return Clock.fixed(LocalDateTime.of(2032, 6, 15, 23, 30).toInstant(ZoneOffset.ofHours(-5)),
                    ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private S3TestData data;
    private int hic;
    private int icv;
    private int general;
    private int cardio;
    private long patient;
    private S3TestData.Professional pro;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        hic = data.siteId("HIC");
        icv = data.siteId("ICV");
        general = data.specialty("GENERAL", 30);
        cardio = data.specialty("SPECIALIZED", 60);
        patient = data.user("Ana", "USER");
        pro = data.professional("daily", new int[] { general, cardio }, hic, icv);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private long seed(LocalDate day, String start, String end, int site, int specialty, String status) {
        return data.appointment(patient, pro.id(), site, specialty, day, java.time.LocalTime.parse(start),
                java.time.LocalTime.parse(end), status);
    }

    private ResultActions call(String query) throws Exception {
        return mvc.perform(get(URL + query).header(KEY_HEADER, KEY));
    }

    private String raw(String query) throws Exception {
        return call(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ============================================================ clave y metodo

    @Test
    void withoutKeyReturns401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void wrongKeyReturns401AndPersonJwtDoesNotReplaceTheKey() throws Exception {
        mvc.perform(get(URL).header(KEY_HEADER, "otra-clave")).andExpect(status().isUnauthorized());
        TestTokens tokens = new TestTokens(jwtEncoder);
        long admin = data.user("adm", "ADMIN");
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, tokens.bearer(admin, Role.ADMIN)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writeMethodsWithValidKeyReturn405() throws Exception {
        for (String method : WRITE_METHODS) {
            mvc.perform(request(HttpMethod.valueOf(method), URL).header(KEY_HEADER, KEY)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string(HttpHeaders.ALLOW, "GET"));
        }
    }

    // ============================================================ contenido

    @Test
    void dayWithoutAppointmentsReturnsEmptyRowsAndRealPendingCounts() throws Exception {
        seed(DAY, "08:00", "08:30", hic, general, "REQUESTED"); // otro dia: cuenta como pendiente global
        long expectedPending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments a JOIN appointment_statuses s ON s.id = a.status_id
                WHERE s.code = 'REQUESTED'""", Long.class);
        long expectedReschedules = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reschedule_requests WHERE decided_at IS NULL", Long.class);

        call("?date=" + EMPTY_DAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2032-07-20"))
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows").isEmpty())
                .andExpect(jsonPath("$.pending.pendingRequests").value(expectedPending))
                .andExpect(jsonPath("$.pending.pendingReschedules").value(expectedReschedules));
        assertThat(expectedPending).isGreaterThanOrEqualTo(1);
    }

    @Test
    void mixedStatusesAndSitesAreListedSortedByStartTimeWithExactKeys() throws Exception {
        seed(DAY, "14:00", "14:30", icv, general, "CANCELLED");
        seed(DAY, "08:00", "08:30", hic, general, "APPROVED");
        seed(DAY, "11:00", "12:00", hic, cardio, "REQUESTED");
        seed(DAY, "09:30", "10:00", icv, general, "APPROVED");
        seed(DAY, "16:00", "16:30", hic, general, "NO_SHOW");
        seed(DAY.plusDays(1), "07:00", "07:30", hic, general, "APPROVED"); // otro dia: fuera

        JsonNode root = json.readTree(raw("?date=" + DAY));
        JsonNode rows = root.get("rows");
        assertThat(rows.size()).isGreaterThanOrEqualTo(5);

        List<String> starts = new ArrayList<>();
        for (JsonNode n : rows) {
            starts.add(n.get("startTime").asText());
            List<String> keys = new ArrayList<>();
            n.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).containsExactlyInAnyOrder("siteCode", "status", "specialty", "startTime");
        }
        assertThat(starts).isSorted();
        assertThat(starts).doesNotContain("07:00");

        List<String> mine = new ArrayList<>();
        for (JsonNode n : rows) {
            mine.add(n.get("startTime").asText() + "|" + n.get("siteCode").asText() + "|"
                    + n.get("status").asText());
        }
        assertThat(mine).contains("08:00|HIC|APPROVED", "09:30|ICV|APPROVED", "11:00|HIC|REQUESTED",
                "14:00|ICV|CANCELLED", "16:00|HIC|NO_SHOW");
        assertThat(mine.indexOf("08:00|HIC|APPROVED")).isLessThan(mine.indexOf("09:30|ICV|APPROVED"));
        assertThat(mine.indexOf("11:00|HIC|REQUESTED")).isLessThan(mine.indexOf("14:00|ICV|CANCELLED"));
    }

    @Test
    void ties_on_startTime_are_broken_by_siteCode_then_status_then_specialtyName() throws Exception {
        LocalDate day = LocalDate.of(2032, 8, 10);
        // Misma hora 09:00; sembradas en orden inverso al esperado.
        seed(day, "09:00", "09:30", icv, general, "APPROVED");
        seed(day, "09:00", "10:00", hic, cardio, "REQUESTED");
        seed(day, "09:00", "09:30", hic, general, "REQUESTED");
        seed(day, "09:00", "09:30", hic, general, "APPROVED");
        seed(day, "09:00", "10:00", hic, cardio, "APPROVED");

        JsonNode rows = json.readTree(raw("?date=" + day)).get("rows");
        List<String> got = new ArrayList<>();
        for (JsonNode n : rows) {
            got.add(n.get("siteCode").asText() + "|" + n.get("status").asText() + "|"
                    + n.get("specialty").asText());
        }
        String gName = jdbc.queryForObject("SELECT name FROM specialties WHERE id = ?", String.class, general);
        String cName = jdbc.queryForObject("SELECT name FROM specialties WHERE id = ?", String.class, cardio);
        List<String> expected = new ArrayList<>(List.of(
                "HIC|APPROVED|" + gName, "HIC|APPROVED|" + cName,
                "HIC|REQUESTED|" + gName, "HIC|REQUESTED|" + cName,
                "ICV|APPROVED|" + gName));
        // Dentro de igual sede y estado, desempata el nombre de especialidad.
        expected.subList(0, 2).sort(String::compareTo);
        expected.subList(2, 4).sort(String::compareTo);
        assertThat(got).isEqualTo(expected);
    }

    @Test
    void dateDefaultsToTodayInBogotaNotUtc() throws Exception {
        seed(DAY, "10:00", "10:30", hic, general, "APPROVED");
        seed(DAY.plusDays(1), "10:00", "10:30", icv, general, "APPROVED");

        JsonNode root = json.readTree(raw(""));
        assertThat(root.get("date").asText()).isEqualTo("2032-06-15");
        boolean hasHic = false;
        boolean hasIcv = false;
        for (JsonNode n : root.get("rows")) {
            hasHic |= n.get("siteCode").asText().equals("HIC");
            hasIcv |= n.get("siteCode").asText().equals("ICV");
        }
        assertThat(hasHic).isTrue();
        assertThat(hasIcv).isFalse();
    }

    @Test
    void invalidDateReturns400ValidationOnDate() throws Exception {
        for (String bad : new String[] { "abc", "", "2032-13-45", "15/06/2032", "2032-02-30" }) {
            call("?date=" + bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.fieldErrors.date").isNotEmpty());
        }
    }

    @Test
    void responseHasNoPiiKeysAndNoSeededDocumentOrPhone() throws Exception {
        seed(DAY, "08:00", "08:30", hic, general, "APPROVED");
        String document = jdbc.queryForObject("SELECT document_number FROM users WHERE id = ?", String.class, patient);
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, patient);
        String raw = raw("?date=" + DAY);
        JsonNode root = json.readTree(raw);

        List<String> top = new ArrayList<>();
        root.fieldNames().forEachRemaining(top::add);
        assertThat(top).containsExactlyInAnyOrder("date", "rows", "pending");
        List<String> pending = new ArrayList<>();
        root.get("pending").fieldNames().forEachRemaining(pending::add);
        assertThat(pending).containsExactlyInAnyOrder("pendingRequests", "pendingReschedules");

        assertThat(raw).doesNotContain(document).doesNotContain("3001234567").doesNotContain(email)
                .doesNotContain("Ana").doesNotContain("Prueba").doesNotContain(String.valueOf(patient) + ",")
                .doesNotContain("documentNumber").doesNotContain("phone").doesNotContain("email")
                .doesNotContain("patient").doesNotContain("firstName").doesNotContain("Name")
                .doesNotContain("appointmentId");
    }
}
