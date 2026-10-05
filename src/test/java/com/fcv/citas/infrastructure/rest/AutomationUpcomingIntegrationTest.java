package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * HU-034 (CA-01 a CA-07): {@code GET /api/automation/appointments/upcoming} con clave de API, cadena de
 * seguridad propia, ventana en America/Bogota, vista minima y {@code hours} acotado.
 *
 * <p>El reloj se fija (2032-06-15 10:00 hora de Bogota) para poder probar los bordes exactos de la
 * ventana; las citas se siembran en esa fecha lejana, que ninguna otra prueba usa.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutomationUpcomingIntegrationTest {

    /** Misma clave que {@code application-test.yml}. */
    private static final String KEY = "test-only-automation-key-0123456789abcdef";
    private static final String URL = "/api/automation/appointments/upcoming";
    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final String KEY_HEADER = "X-Automation-Key";
    /** Ahora fijo, en hora de Bogota (UTC-5, sin DST). */
    private static final LocalDateTime NOW = LocalDateTime.of(2032, 6, 15, 10, 0);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.toInstant(ZoneOffset.ofHours(-5)), ZoneOffset.UTC);
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
    private int specialty;
    private long patient;
    private S3TestData.Professional pro;
    private final Set<Long> seeded = new HashSet<>();

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        hic = data.siteId("HIC");
        specialty = data.specialty("GENERAL", 30);
        patient = data.user("Ana", "USER");
        pro = data.professional("auto", new int[] { specialty }, hic);
        seeded.clear();
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private long seed(LocalDateTime start, String status) {
        long id = data.appointment(patient, pro.id(), hic, specialty, start.toLocalDate(), start.toLocalTime(),
                start.toLocalTime().plusMinutes(30), status);
        seeded.add(id);
        return id;
    }

    private ResultActions call(String query) throws Exception {
        return mvc.perform(get(URL + query).header(KEY_HEADER, KEY));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Ids devueltos que pertenecen a lo sembrado por esta prueba, en el orden de la respuesta. */
    private List<Long> mine(String query) throws Exception {
        JsonNode root = body(call(query).andExpect(status().isOk()));
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : root) {
            long id = n.get("appointmentId").asLong();
            if (seeded.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    // ============================================================ CA-01 / CA-02: sin clave o clave mala

    @Test
    void withoutKeyHeaderReturns401ProblemInSpanish() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void wrongKeyAndDifferentLengthKeyAndEmptyKeyReturnTheSame401BodyAsNoKey() throws Exception {
        String noKey = mvc.perform(get(URL)).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        for (String bad : new String[] { "x".repeat(KEY.length()), "corta", KEY + "extra", "" }) {
            String response = mvc.perform(get(URL).header(KEY_HEADER, bad))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("No autenticado"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).isEqualTo(noKey);
            if (!bad.isEmpty()) {
                assertThat(response).doesNotContain(bad);
            }
        }
    }

    // ============================================================ CA-03: un JWT de persona no sustituye la clave

    @Test
    void personJwtWithoutKeyReturns401ForEveryRole() throws Exception {
        TestTokens tokens = new TestTokens(jwtEncoder);
        long admin = data.user("adm", "ADMIN");
        for (Role role : new Role[] { Role.ADMIN, Role.PROFESSIONAL, Role.USER }) {
            mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, tokens.bearer(admin, role)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void personJwtWithWrongKeyReturns401() throws Exception {
        TestTokens tokens = new TestTokens(jwtEncoder);
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, tokens.bearer(data.user("adm", "ADMIN"), Role.ADMIN))
                .header(KEY_HEADER, "otra-clave"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validKeyDoesNotOpenPersonRoutes() throws Exception {
        for (String path : new String[] { "/api/admin/appointments", "/api/professional/appointments",
                "/api/patient/appointments", "/api/me" }) {
            int code = mvc.perform(get(path).header(KEY_HEADER, KEY)).andReturn().getResponse().getStatus();
            assertThat(code).as(path).isIn(401, 403);
        }
    }

    // ============================================================ CA-07: solo GET

    @Test
    void writeMethodsWithValidKeyReturn405AndNeverExecute() throws Exception {
        long id = seed(NOW.plusHours(2), "APPROVED");
        String sql = "SELECT s.code FROM appointments a JOIN appointment_statuses s"
                + " ON s.id = a.status_id WHERE a.id = ?";
        int appointments = data.count("SELECT COUNT(*) FROM appointments");
        for (String method : WRITE_METHODS) {
            mvc.perform(request(HttpMethod.valueOf(method), URL).header(KEY_HEADER, KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        assertThat(jdbc.queryForObject(sql, String.class, id)).isEqualTo("APPROVED");
        assertThat(data.count("SELECT COUNT(*) FROM appointments")).isEqualTo(appointments);
    }

    @Test
    void wrongOrMissingKeyWithAnyMethodReturns401NotMethodNotAllowed() throws Exception {
        for (String method : WRITE_METHODS) {
            mvc.perform(request(HttpMethod.valueOf(method), URL).header(KEY_HEADER, "clave-erronea").contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andExpect(status().isUnauthorized());
            mvc.perform(request(HttpMethod.valueOf(method), URL).contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================================================ CA-04: estados, ventana, borde, orden

    @Test
    void approvedInWindowAppearsWithAllContractFields() throws Exception {
        long id = seed(NOW.plusHours(2), "APPROVED");
        call("").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.appointmentId == " + id + ")].patientFirstName").value("Ana"))
                .andExpect(jsonPath("$[?(@.appointmentId == " + id + ")].date").value("2032-06-15"))
                .andExpect(jsonPath("$[?(@.appointmentId == " + id + ")].startTime").value("12:00"))
                .andExpect(jsonPath("$[?(@.appointmentId == " + id + ")].endTime").value("12:30"))
                .andExpect(jsonPath("$[?(@.appointmentId == " + id + ")].site.code").value("HIC"))
                .andExpect(jsonPath("$[?(@.appointmentId == " + id + ")].professional").isNotEmpty())
                .andExpect(jsonPath("$[?(@.appointmentId == " + id + ")].specialty").isNotEmpty());
        JsonNode item = null;
        for (JsonNode n : body(call(""))) {
            if (n.get("appointmentId").asLong() == id) {
                item = n;
            }
        }
        assertThat(item).isNotNull();
        assertThat(item.get("patientEmail").asText()).endsWith(S3TestData.DOMAIN);
        assertThat(item.get("site").get("name").asText()).isNotBlank();
        assertThat(item.get("site").get("address").asText()).isNotBlank();
    }

    @Test
    void onlyApprovedStatusIsReturned() throws Exception {
        long approved = seed(NOW.plusHours(2), "APPROVED");
        int hour = 3;
        for (String s : List.of("REQUESTED", "REJECTED", "CANCELLED", "COMPLETED", "NO_SHOW")) {
            seed(NOW.plusHours(hour++), s);
        }
        assertThat(mine("")).containsExactly(approved);
    }

    @Test
    void pastAndBeyondWindowAreExcluded() throws Exception {
        long inside = seed(NOW.plusHours(5), "APPROVED");
        seed(NOW.minusHours(3), "APPROVED");
        seed(NOW.plusHours(25), "APPROVED");
        assertThat(mine("?hours=24")).containsExactly(inside);
    }

    @Test
    void exactlyNowPlusHoursIsIncludedAndExactlyNowIsExcluded() throws Exception {
        seed(NOW, "APPROVED");
        long edge = seed(NOW.plusHours(24), "APPROVED");
        assertThat(mine("?hours=24")).containsExactly(edge);
    }

    @Test
    void hoursParameterWidensTheWindowAndDefaultsTo24() throws Exception {
        long in24 = seed(NOW.plusHours(20), "APPROVED");
        long in48 = seed(NOW.plusHours(48), "APPROVED");
        assertThat(mine("")).containsExactly(in24);
        assertThat(mine("?hours=48")).containsExactly(in24, in48);
        assertThat(mine("?hours=1")).isEmpty();
        assertThat(mine("?hours=72")).containsExactly(in24, in48);
    }

    @Test
    void resultsAreOrderedByDateAndStartTimeAscending() throws Exception {
        long third = seed(NOW.plusHours(23), "APPROVED");
        long first = seed(NOW.plusHours(1), "APPROVED");
        long second = seed(NOW.plusHours(14), "APPROVED");
        assertThat(mine("")).containsExactly(first, second, third);
    }

    // ============================================================ CA-05: minimizacion de datos

    @Test
    void responseHasExactlyTheContractKeysAndNoDocumentPhoneOrHistory() throws Exception {
        seed(NOW.plusHours(2), "APPROVED");
        String document = jdbc.queryForObject("SELECT document_number FROM users WHERE id = ?", String.class, patient);
        String raw = call("").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(raw);
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode n : root) {
            List<String> keys = new ArrayList<>();
            n.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).containsExactlyInAnyOrder("appointmentId", "patientFirstName", "patientEmail", "date",
                    "startTime", "endTime", "site", "professional", "specialty");
            List<String> siteKeys = new ArrayList<>();
            n.get("site").fieldNames().forEachRemaining(siteKeys::add);
            assertThat(siteKeys).containsExactlyInAnyOrder("code", "name", "address");
            assertThat(n.has("documentNumber")).isFalse();
            assertThat(n.has("phone")).isFalse();
            assertThat(n.has("history")).isFalse();
        }
        assertThat(raw).doesNotContain("documentNumber").doesNotContain("phone").doesNotContain("history")
                .doesNotContain(document).doesNotContain("3001234567");
    }

    // ============================================================ CA-06: hours acotado

    @Test
    void hoursOutOfRangeOrNotNumericReturns400Validation() throws Exception {
        for (String bad : new String[] { "0", "73", "-1", "abc", "" }) {
            call("?hours=" + bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.fieldErrors.hours").isNotEmpty());
        }
    }

    @Test
    void hoursAtTheLimitsReturns200() throws Exception {
        call("?hours=1").andExpect(status().isOk());
        call("?hours=72").andExpect(status().isOk());
    }
}
