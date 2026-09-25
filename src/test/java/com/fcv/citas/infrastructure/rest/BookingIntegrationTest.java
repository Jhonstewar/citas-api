package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-022 (disponibilidad), HU-023 (general), HU-024 (especializada, GOAL_02), HU-025 (mis citas) y
 * HU-032 (historial). Incluye la prueba de doble reserva concurrente (verificacion 5 de S3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingIntegrationTest {

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
    private TestTokens tokens;
    private int hic;
    private int icv;
    private int general;
    private int cardiology;
    private S3TestData.Professional gp;
    private S3TestData.Professional cardiologist;
    private long patient;
    private String patientToken;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        icv = data.siteId("ICV");
        general = data.generalMedicineId();
        cardiology = data.specialty("SPECIALIZED", 60);
        gp = data.professional("gp", new int[] { general }, hic);
        cardiologist = data.professional("cardio", new int[] { cardiology }, hic, icv);
        patient = data.user("patient", "USER");
        patientToken = tokens.bearer(patient, Role.USER);
        day = LocalDate.now(SystemZone.ZONE).plusDays(5);
        data.block(gp.id(), hic, day, "08:00", "10:00");
        data.block(cardiologist.id(), hic, day, "08:00", "10:00");
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private Map<String, Object> body(S3TestData.Professional pro, int specialty, LocalDate date, String start) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("professionalId", pro.id());
        body.put("siteId", hic);
        body.put("specialtyId", specialty);
        body.put("date", date.toString());
        body.put("startTime", start);
        return body;
    }

    private ResultActions book(String flow, String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/patient/appointments/" + flow).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions offers(int specialty, LocalDate date) throws Exception {
        return mvc.perform(get("/api/patient/availability?specialtyId=" + specialty + "&date=" + date)
                .header(HttpHeaders.AUTHORIZATION, patientToken));
    }

    private int appointmentsOf(S3TestData.Professional pro) {
        return data.count("SELECT COUNT(*) FROM appointments WHERE professional_id = ?", pro.id());
    }

    // ================================================================== HU-023 cita general

    /** CA-01, CA-02 y CA-08: APPROVED al instante, slot reservado, historial SYSTEM sin actor. */
    @Test
    void generalAppointmentIsApprovedReservedAndAudited() throws Exception {
        String body = book("general", patientToken, body(gp, general, day, "08:30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.startTime").value("08:30"))
                .andExpect(jsonPath("$.endTime").value("09:00"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.site.code").value("HIC"))
                .andExpect(jsonPath("$.specialty.code").value("MEDICINA_GENERAL"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        assertThat(data.count("SELECT patient_user_id FROM appointments WHERE id = ?", id)).isEqualTo((int) patient);
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", id)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT h.source, h.actor_user_id, s.code AS status"
                + " FROM appointment_status_history h JOIN appointment_statuses s ON s.id = h.status_id"
                + " WHERE h.appointment_id = ?", id))
                .containsEntry("source", "SYSTEM").containsEntry("status", "APPROVED")
                .containsEntry("actor_user_id", null);
        offers(general, day).andExpect(jsonPath("$[*].startTime", not(hasItem("08:30"))))
                .andExpect(jsonPath("$[*].startTime", hasItem("09:00")));
    }

    /** CA-03 (verificacion 5): el mismo slot dos veces → 409 SLOT_TAKEN, sin cita ni historial nuevos. */
    @Test
    void doubleBookingIsRejectedWith409() throws Exception {
        book("general", patientToken, body(gp, general, day, "08:00")).andExpect(status().isCreated());
        String other = tokens.bearer(data.user("other", "USER"), Role.USER);
        book("general", other, body(gp, general, day, "08:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
        assertThat(appointmentsOf(gp)).isEqualTo(1);
        assertThat(data.count("SELECT COUNT(*) FROM appointment_status_history h JOIN appointments a"
                + " ON a.id = h.appointment_id WHERE a.professional_id = ?", gp.id())).isEqualTo(1);
    }

    /** Resultado de un intento de reserva: estado HTTP y el {@code code} del ProblemDetail (null si gano). */
    private record Attempt(int status, String code) {
    }

    /** Ejecuta un intento de reserva y devuelve estado y {@code code}, no solo el estado. */
    private Attempt attempt(String flow, String token, String payload) throws Exception {
        var response = mvc.perform(post("/api/patient/appointments/" + flow)
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andReturn().getResponse();
        String content = response.getContentAsString();
        String code = null;
        if (!content.isBlank()) {
            var node = json.readTree(content).get("code");
            code = node == null || node.isNull() ? null : node.asText();
        }
        return new Attempt(response.getStatus(), code);
    }

    /**
     * CA-04 (verificacion 5): 8 confirmaciones concurrentes del mismo slot → exactamente una gana.
     * La garantia la da la PK de {@code slot_reservations}, no una comprobacion previa.
     *
     * <p>Los 7 perdedores tienen que traer {@code code = SLOT_TAKEN} (lo traduce el adaptador de
     * persistencia al fallar el INSERT), no {@code CONCURRENT_CHANGE} (la red de seguridad de
     * {@code GlobalExceptionHandler} para violaciones de integridad que escapan al commit): afirmar
     * solo el 409 dejaria pasar cualquiera de los dos caminos.</p>
     */
    @Test
    void concurrentBookingsOfTheSameSlotLetExactlyOneWin() throws Exception {
        int contenders = 8;
        List<String> patients = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            patients.add(tokens.bearer(data.user("race" + i, "USER"), Role.USER));
        }
        String payload = json.writeValueAsString(body(gp, general, day, "09:00"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            List<Future<Attempt>> results = new ArrayList<>();
            for (String token : patients) {
                Callable<Attempt> race = () -> {
                    start.await();
                    return attempt("general", token, payload);
                };
                results.add(pool.submit(race));
            }
            start.countDown();
            List<Attempt> attempts = new ArrayList<>();
            for (Future<Attempt> r : results) {
                attempts.add(r.get());
            }
            assertThat(attempts).filteredOn(a -> a.status() == 201).hasSize(1);
            assertThat(attempts).filteredOn(a -> a.status() == 409).hasSize(contenders - 1)
                    .extracting(Attempt::code).containsOnly("SLOT_TAKEN");
        } finally {
            pool.shutdownNow();
        }
        assertThat(appointmentsOf(gp)).isEqualTo(1);
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations r JOIN availability_slots s ON s.id = r.slot_id"
                + " JOIN availability_blocks b ON b.id = s.availability_block_id WHERE b.professional_id = ?",
                gp.id())).isEqualTo(1);
    }

    /**
     * HU-024 DoD: la no-doble-reserva <b>entre flujo general y especializado</b> contra MySQL 8.4.
     * El profesional atiende una especialidad general de 30 min y una especializada de 30 min, asi que
     * ambos flujos compiten por el mismo unico slot. Dos pacientes distintos disparan a la vez
     * {@code /general} y {@code /specialized}: gana uno solo y la carrera decide cual, por lo que la
     * prueba acepta cualquiera de los dos ganadores y comprueba el estado e historial que le toca.
     */
    @Test
    void concurrentGeneralAndSpecializedOnTheSameSlotLetExactlyOneWin() throws Exception {
        int generalShort = data.specialty("GENERAL", 30);
        int specializedShort = data.specialty("SPECIALIZED", 30);
        S3TestData.Professional both = data.professional("both",
                new int[] { generalShort, specializedShort }, hic);
        long block = data.block(both.id(), hic, day, "11:00", "12:00");
        long slot = data.slotId(block, "11:00:00");
        long generalPatient = data.user("crossGeneral", "USER");
        long specializedPatient = data.user("crossSpecialized", "USER");
        String generalToken = tokens.bearer(generalPatient, Role.USER);
        String specializedToken = tokens.bearer(specializedPatient, Role.USER);
        String generalPayload = json.writeValueAsString(body(both, generalShort, day, "11:00"));
        String specializedPayload = json.writeValueAsString(body(both, specializedShort, day, "11:00"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Attempt> attempts = new ArrayList<>();
        try {
            Callable<Attempt> generalRace = () -> {
                start.await();
                return attempt("general", generalToken, generalPayload);
            };
            Callable<Attempt> specializedRace = () -> {
                start.await();
                return attempt("specialized", specializedToken, specializedPayload);
            };
            Future<Attempt> generalResult = pool.submit(generalRace);
            Future<Attempt> specializedResult = pool.submit(specializedRace);
            start.countDown();
            attempts.add(generalResult.get());
            attempts.add(specializedResult.get());
        } finally {
            pool.shutdownNow();
        }

        assertThat(attempts).filteredOn(a -> a.status() == 201).hasSize(1);
        assertThat(attempts).filteredOn(a -> a.status() == 409).hasSize(1)
                .extracting(Attempt::code).containsOnly("SLOT_TAKEN");
        // Libro unico de ocupacion (dec-003): una sola fila para el slot en disputa.
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE slot_id = ?", slot)).isEqualTo(1);
        assertThat(appointmentsOf(both)).isEqualTo(1);

        Map<String, Object> winner = jdbc.queryForMap("SELECT a.specialty_id, a.patient_user_id, st.code AS status,"
                + " h.source, h.actor_user_id FROM appointments a"
                + " JOIN appointment_statuses st ON st.id = a.status_id"
                + " JOIN appointment_status_history h ON h.appointment_id = a.id"
                + " WHERE a.professional_id = ?", both.id());
        if (((Number) winner.get("specialty_id")).intValue() == generalShort) {
            assertThat(winner).containsEntry("status", "APPROVED").containsEntry("source", "SYSTEM")
                    .containsEntry("actor_user_id", null);
            assertThat(((Number) winner.get("patient_user_id")).longValue()).isEqualTo(generalPatient);
        } else {
            assertThat(winner).containsEntry("status", "REQUESTED").containsEntry("source", "USER");
            assertThat(((Number) winner.get("actor_user_id")).longValue()).isEqualTo(specializedPatient);
            assertThat(((Number) winner.get("patient_user_id")).longValue()).isEqualTo(specializedPatient);
        }
    }

    /** CA-06 (RN-06): franja pasada → 422 PAST_TIME. */
    @Test
    void pastSlotCannotBeBooked() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        data.block(gp.id(), hic, yesterday, "08:00", "09:00");
        book("general", patientToken, body(gp, general, yesterday, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAST_TIME"));
        assertThat(appointmentsOf(gp)).isZero();
    }

    /** CA-07: especializada por el flujo general → 422 WRONG_FLOW; y al reves. */
    @Test
    void wrongFlowIsRejectedBothWays() throws Exception {
        book("general", patientToken, body(cardiologist, cardiology, day, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_FLOW"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("especializada")));
        book("specialized", patientToken, body(gp, general, day, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_FLOW"));
    }

    /** CA-07 (RN-08): especialidad no asociada al profesional → 422. */
    @Test
    void specialtyNotAssignedToTheProfessionalIsRejected() throws Exception {
        book("specialized", patientToken, body(gp, cardiology, day, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPECIALTY_NOT_ASSIGNED"));
    }

    /** CA-09: solo USER agenda; el titular es el del token aunque el cuerpo diga otro. */
    @Test
    void onlyUsersBookAndAlwaysForThemselves() throws Exception {
        book("general", tokens.bearer(gp.userId(), Role.PROFESSIONAL), body(gp, general, day, "08:00"))
                .andExpect(status().isForbidden());
        Map<String, Object> spoofed = body(gp, general, day, "08:00");
        spoofed.put("patientUserId", 1);
        String response = book("general", patientToken, spoofed).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        assertThat(data.count("SELECT patient_user_id FROM appointments WHERE id = ?", id)).isEqualTo((int) patient);
    }

    // ================================================================== HU-024 especializada (GOAL_02)

    /** CA-01, CA-02, CA-04, CA-07 y CA-08: REQUESTED, 2 slots retenidos, historial USER con actor. */
    @Test
    void specializedRequestRetainsTwoConsecutiveSlots() throws Exception {
        String response = book("specialized", patientToken, body(cardiologist, cardiology, day, "08:30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.endTime").value("09:30"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        assertThat(jdbc.queryForList("SELECT slot_order FROM slot_reservations WHERE appointment_id = ?"
                + " ORDER BY slot_order", Integer.class, id)).containsExactly(1, 2);
        Map<String, Object> history = jdbc.queryForMap("SELECT h.source, h.actor_user_id FROM"
                + " appointment_status_history h WHERE h.appointment_id = ?", id);
        assertThat(history.get("source")).isEqualTo("USER");
        assertThat(((Number) history.get("actor_user_id")).longValue()).isEqualTo(patient);
        assertThat(data.count("SELECT COUNT(*) FROM appointment_status_history h JOIN appointment_statuses s"
                + " ON s.id = h.status_id WHERE h.appointment_id = ? AND s.code = 'APPROVED'", id)).isZero();
        // CA-02: la franja retenida deja de ofrecerse y otra reserva recibe 409.
        offers(cardiology, day).andExpect(jsonPath("$[*].startTime", not(hasItem("08:30"))));
        String other = tokens.bearer(data.user("other", "USER"), Role.USER);
        book("specialized", other, body(cardiologist, cardiology, day, "08:30"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    }

    /** CA-04: si el consecutivo esta ocupado se rechaza sin retener el primero. */
    @Test
    void sixtyMinutesWithTakenSecondSlotRetainsNothing() throws Exception {
        book("specialized", patientToken, body(cardiologist, cardiology, day, "08:30")).andExpect(status().isCreated());
        String other = tokens.bearer(data.user("other", "USER"), Role.USER);
        book("specialized", other, body(cardiologist, cardiology, day, "08:00"))
                .andExpect(status().isConflict());
        long firstSlot = jdbc.queryForObject("SELECT s.id FROM availability_slots s JOIN availability_blocks b"
                + " ON b.id = s.availability_block_id WHERE b.professional_id = ? AND s.start_time = '08:00:00'",
                Long.class, cardiologist.id());
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE slot_id = ?", firstSlot)).isZero();
    }

    /** CA-04 y D9: 60 min que se sale del bloque → 422 SLOT_NOT_AVAILABLE. */
    @Test
    void sixtyMinutesMustFitInsideTheBlock() throws Exception {
        book("specialized", patientToken, body(cardiologist, cardiology, day, "09:30"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
    }

    // ================================================================== HU-022 disponibilidad

    /** CA-01 y CA-02: 30 min ofrece cada slot libre; 60 min solo pares consecutivos del mismo bloque. */
    @Test
    void offersRespectDurationAndReservations() throws Exception {
        book("general", patientToken, body(gp, general, day, "08:30")).andExpect(status().isCreated());
        offers(general, day)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("08:00"))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                .andExpect(jsonPath("$[0].professional.id").value(gp.id()));
        // Bloque 08:00–10:00 de cardiologia (60 min): 08:00, 08:30 y 09:00; nunca 09:30.
        offers(cardiology, day)
                .andExpect(jsonPath("$[*].startTime", org.hamcrest.Matchers.contains("08:00", "08:30", "09:00")))
                .andExpect(jsonPath("$[0].endTime").value("09:00"));
        // Con 09:00 reservado, 08:30 pierde su consecutivo y 09:00 desaparece.
        book("specialized", patientToken, body(cardiologist, cardiology, day, "09:00")).andExpect(status().isCreated());
        offers(cardiology, day).andExpect(jsonPath("$[*].startTime", org.hamcrest.Matchers.contains("08:00")));
    }

    /** CA-06 y HU-016 CA-03/CA-04: profesional inactivo no se ofrece ni recibe reservas. */
    @Test
    void inactiveProfessionalIsNeitherOfferedNorBookable() throws Exception {
        jdbc.update("UPDATE professionals SET active = FALSE WHERE id = ?", gp.id());
        offers(general, day).andExpect(jsonPath("$[*].professional.id", not(hasItem((int) gp.id()))));
        book("general", patientToken, body(gp, general, day, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_INACTIVE"));
    }

    /** CA-08: buscar no retiene nada. CA-05: el filtro de sede acota. */
    @Test
    void searchingRetainsNothingAndSiteFilterNarrows() throws Exception {
        offers(general, day).andExpect(jsonPath("$.length()").value(4));
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations r JOIN availability_slots s ON s.id = r.slot_id"
                + " JOIN availability_blocks b ON b.id = s.availability_block_id WHERE b.professional_id = ?",
                gp.id())).isZero();
        mvc.perform(get("/api/patient/availability?specialtyId=" + general + "&date=" + day + "&siteId=" + icv)
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$[*].professional.id", not(hasItem((int) gp.id()))));
        mvc.perform(get("/api/patient/availability/days?specialtyId=" + general + "&from=" + day + "&to="
                + day.plusDays(3)).header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$[0].date").value(day.toString()))
                .andExpect(jsonPath("$[0].offers").value(4));
    }

    // ================================================================== HU-025 mis citas

    /** CA-01, CA-02, CA-04 y CA-07: solo las propias, filtro por estado, detalle con historial. */
    @Test
    void patientSeesOnlyOwnAppointmentsWithDetail() throws Exception {
        String r1 = book("general", patientToken, body(gp, general, day, "08:00")).andReturn().getResponse()
                .getContentAsString();
        book("specialized", patientToken, body(cardiologist, cardiology, day, "08:00")).andExpect(status().isCreated());
        long otherPatient = data.user("other", "USER");
        String otherToken = tokens.bearer(otherPatient, Role.USER);
        book("general", otherToken, body(gp, general, day, "09:00")).andExpect(status().isCreated());

        mvc.perform(get("/api/patient/appointments").header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/patient/appointments?status=APPROVED").header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
        long id = json.readTree(r1).get("id").asLong();
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professional.fullName").exists())
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].source").value("SYSTEM"))
                .andExpect(jsonPath("$.patient").doesNotExist());
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isNotFound());
    }
}
