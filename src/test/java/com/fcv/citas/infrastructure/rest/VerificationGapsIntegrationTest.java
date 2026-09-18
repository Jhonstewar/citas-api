package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Pruebas que cierran los hallazgos de la verificacion independiente del backend (S3, F11):
 * mutantes vivos M09, M10, M11, M13 y M14, y los hallazgos F1, F4, F5, F6, F8, F9 y F12, mas CA
 * que no tenian prueba dedicada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationGapsIntegrationTest {

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
    private String patientToken;
    private String admin;
    private LocalDate day;
    private LocalDate yesterday;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        icv = data.siteId("ICV");
        general = data.generalMedicineId();
        cardiology = data.specialty("SPECIALIZED", 60);
        gp = data.professional("gp", new int[] { general }, hic);
        cardiologist = data.professional("cardio", new int[] { cardiology }, hic);
        patientToken = tokens.bearer(data.user("patient", "USER"), Role.USER);
        admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        day = LocalDate.now(SystemZone.ZONE).plusDays(6);
        yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        data.block(gp.id(), hic, day, "08:00", "10:00");
        data.block(cardiologist.id(), hic, day, "08:00", "10:00");
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private Map<String, Object> booking(S3TestData.Professional pro, int specialty, LocalDate date, String start) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("professionalId", pro.id());
        body.put("siteId", hic);
        body.put("specialtyId", specialty);
        body.put("date", date.toString());
        body.put("startTime", start);
        return body;
    }

    private ResultActions book(String flow, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/patient/appointments/" + flow).header(HttpHeaders.AUTHORIZATION, patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
            String token, Object body) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    // ------------------------------------------------------------------ mutantes vivos

    /** M09 (RN-08, HU-023 CA-07 / HU-024 CA-06): especialidad desactivada → 422, nada persiste. */
    @Test
    void bookingWithAnInactiveSpecialtyIsRejected() throws Exception {
        jdbc.update("UPDATE specialties SET active = FALSE WHERE id = ?", cardiology);
        book("specialized", booking(cardiologist, cardiology, day, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPECIALTY_INACTIVE"));
        assertThat(data.count("SELECT COUNT(*) FROM appointments WHERE professional_id = ?", cardiologist.id()))
                .isZero();
    }

    /** M10 (RN-06, HU-022 CA-04): la busqueda no ofrece franjas pasadas. */
    @Test
    void searchNeverOffersPastSlots() throws Exception {
        data.block(gp.id(), hic, yesterday, "08:00", "10:00");
        mvc.perform(get("/api/patient/availability?specialtyId=" + general + "&date=" + yesterday)
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** M11 (RN-08, HU-022 CA-06 / HU-011 CA-06): una especialidad desactivada deja de ofrecerse. */
    @Test
    void deactivatedSpecialtyIsNoLongerOffered() throws Exception {
        mvc.perform(get("/api/patient/availability?specialtyId=" + cardiology + "&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$.length()").value(3));
        jdbc.update("UPDATE specialties SET active = FALSE WHERE id = ?", cardiology);
        mvc.perform(get("/api/patient/availability?specialtyId=" + cardiology + "&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** M13 (RN-07): si al profesional le quitan la sede, ni se ofrece ni se reserva alli. */
    @Test
    void siteNoLongerAssignedIsNeitherOfferedNorBookable() throws Exception {
        jdbc.update("UPDATE professional_sites SET site_id = ? WHERE professional_id = ?", icv, gp.id());
        mvc.perform(get("/api/patient/availability?specialtyId=" + general + "&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$[*].professional.id", not(hasItem((int) gp.id()))));
        book("general", booking(gp, general, day, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SITE_NOT_ASSIGNED"));
    }

    /** M14 (RN-06, HU-018 CA-03): un bloque pasado no se edita ni se elimina. */
    @Test
    void pastBlockCannotBeEditedNorDeleted() throws Exception {
        long past = data.block(gp.id(), hic, yesterday, "08:00", "10:00");
        String proToken = tokens.bearer(gp.userId(), Role.PROFESSIONAL);
        mvc.perform(delete("/api/professional/blocks/" + past).header(HttpHeaders.AUTHORIZATION, proToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAST_TIME"));
        send(put("/api/professional/blocks/" + past), proToken,
                Map.of("siteId", hic, "date", day.toString(), "startTime", "14:00", "endTime", "15:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAST_TIME"));
        assertThat(data.count("SELECT COUNT(*) FROM availability_slots WHERE availability_block_id = ?", past))
                .isEqualTo(4);
    }

    // ------------------------------------------------------------------ CA sin prueba dedicada

    /** HU-018 CA-05: la edicion mantiene las reglas de solape y de sede. */
    @Test
    void editingKeepsOverlapAndSiteRules() throws Exception {
        String proToken = tokens.bearer(gp.userId(), Role.PROFESSIONAL);
        long other = data.block(gp.id(), hic, day, "14:00", "16:00");
        send(put("/api/professional/blocks/" + other), proToken,
                Map.of("siteId", hic, "date", day.toString(), "startTime", "09:00", "endTime", "11:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_OVERLAP"));
        send(put("/api/professional/blocks/" + other), proToken,
                Map.of("siteId", icv, "date", day.toString(), "startTime", "14:00", "endTime", "16:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SITE_NOT_ASSIGNED"));
    }

    /** HU-025 CA-03: filtro por fecha, combinable con el de estado. */
    @Test
    void myAppointmentsFilterByDate() throws Exception {
        LocalDate later = day.plusDays(1);
        data.block(gp.id(), hic, later, "08:00", "09:00");
        book("general", booking(gp, general, day, "08:00")).andExpect(status().isCreated());
        book("general", booking(gp, general, later, "08:00")).andExpect(status().isCreated());
        mvc.perform(get("/api/patient/appointments?date=" + later + "&status=APPROVED")
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(later.toString()));
    }

    /** HU-023 CA-05: una especialidad GENERAL de 60 min reserva dos slots consecutivos. */
    @Test
    void generalSixtyMinutesReservesTwoConsecutiveSlots() throws Exception {
        int longGeneral = data.specialty("GENERAL", 60);
        S3TestData.Professional pro = data.professional("long", new int[] { longGeneral }, hic);
        data.block(pro.id(), hic, day, "08:00", "10:00");
        String body = book("general", booking(pro, longGeneral, day, "08:30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.endTime").value("09:30"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        assertThat(jdbc.queryForList("SELECT slot_order FROM slot_reservations WHERE appointment_id = ?"
                + " ORDER BY slot_order", Integer.class, id)).containsExactly(1, 2);
    }

    // ------------------------------------------------------------------ hallazgos F1..F12

    /** F1: no se cambia el tipo de una especialidad en uso (la bandeja perderia solicitudes). */
    @Test
    void typeOfASpecialtyInUseCannotChange() throws Exception {
        send(put("/api/admin/specialties/" + cardiology), admin,
                Map.of("name", "Cardiología X", "appointmentType", "GENERAL", "durationMinutes", 60))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIALTY_REFERENCED"));
        send(put("/api/admin/specialties/" + cardiology), admin,
                Map.of("name", "Cardiología X", "appointmentType", "SPECIALIZED", "durationMinutes", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(30));
    }

    /** F6 (DoD HU-011): nombre unico sin distinguir mayusculas. */
    @Test
    void specialtyNameIsUniqueIgnoringCase() throws Exception {
        send(post("/api/admin/specialties"), admin, Map.of("code", S3TestData.uniqueCode(S3TestData.SPECIALTY_PREFIX),
                "name", "MEDICINA general", "appointmentType", "GENERAL", "durationMinutes", 30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    /** F4 (RF-10, HU-022 CA-05): filtro por tipo de cita sin especialidad; sin ninguno de los dos → 400. */
    @Test
    void availabilityFiltersByAppointmentTypeWithoutSpecialty() throws Exception {
        mvc.perform(get("/api/patient/availability?appointmentType=SPECIALIZED&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].specialty.appointmentType", everyItem(is("SPECIALIZED"))))
                .andExpect(jsonPath("$[*].professional.id", hasItem((int) cardiologist.id())))
                .andExpect(jsonPath("$[*].professional.id", not(hasItem((int) gp.id()))));
        mvc.perform(get("/api/patient/availability?date=" + day).header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.specialtyId").exists());
    }

    /** F5 (HU-010 CA-04): catalogo de estados de reprogramacion con PENDING. */
    @Test
    void rescheduleStatusesCatalogIncludesPending() throws Exception {
        mvc.perform(get("/api/catalogs/reschedule-statuses").header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("PENDING")));
    }

    /** F8: rechazar sin motivo una cita ya decidida es un 409 de transicion, no un 400. */
    @Test
    void rejectingAnApprovedAppointmentIs409EvenWithoutReason() throws Exception {
        String body = book("general", booking(gp, general, day, "08:00")).andReturn().getResponse()
                .getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        send(post("/api/admin/appointments/" + id + "/reject"), admin, Map.of("reason", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    /** F9: el paciente ve "Administración", no el nombre del ADMIN; el ADMIN si lo ve. */
    @Test
    void patientSeesTheRoleNotTheNameOfTheAdmin() throws Exception {
        String body = book("specialized", booking(cardiologist, cardiology, day, "08:00")).andReturn()
                .getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        send(post("/api/admin/appointments/" + id + "/reject"), admin, Map.of("reason", "Sin cupo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[1].actorName").value("admin Prueba"));
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$.history[1].actorName").value("Administración"));
    }

    /**
     * F12 (HU-013 CA-06): altas simultaneas con el mismo codigo profesional → una gana y no queda
     * ningun usuario huerfano: la violacion del perfil deshace tambien el usuario creado.
     */
    @Test
    void concurrentProfessionalCreationsLeaveNoOrphanUsers() throws Exception {
        String code = S3TestData.uniqueCode("P");
        int contenders = 4;
        List<String> emails = new ArrayList<>();
        List<Callable<Integer>> attempts = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < contenders; i++) {
            String email = S3TestData.email("race" + i);
            emails.add(email);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("firstNames", "Carrera");
            body.put("lastNames", "Prueba");
            body.put("documentType", "CC");
            body.put("documentNumber", S3TestData.document());
            body.put("email", email);
            body.put("phone", "3001234567");
            body.put("password", S3TestData.PASSWORD);
            body.put("professionalCode", code);
            body.put("licenseNumber", "LIC-" + code + "-" + i);
            body.put("specialtyIds", List.of(general));
            body.put("primarySpecialtyId", general);
            body.put("siteIds", List.of(hic));
            String payload = json.writeValueAsString(body);
            attempts.add(() -> {
                start.await();
                return mvc.perform(post("/api/admin/professionals").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse()
                        .getStatus();
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> a : attempts) {
                futures.add(pool.submit(a));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(contenders - 1);
        int users = 0;
        for (String email : emails) {
            users += data.count("SELECT COUNT(*) FROM users WHERE email = ?", email);
        }
        assertThat(users).as("usuarios creados por las altas simultaneas").isEqualTo(1);
    }

    /** HU-013 CA-03 y CA-04: matricula y documento duplicados identifican su campo. */
    @Test
    void duplicateLicenseAndDocumentIdentifyTheirField() throws Exception {
        String document = S3TestData.document();
        String license = "LIC-" + S3TestData.uniqueCode("L");
        Map<String, Object> first = professionalBody(S3TestData.email("a"), document, S3TestData.uniqueCode("P"), license);
        send(post("/api/admin/professionals"), admin, first).andExpect(status().isCreated());
        send(post("/api/admin/professionals"), admin,
                professionalBody(S3TestData.email("b"), S3TestData.document(), S3TestData.uniqueCode("P"), license))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.field").value("licenseNumber"));
        send(post("/api/admin/professionals"), admin,
                professionalBody(S3TestData.email("c"), document, S3TestData.uniqueCode("P"), "LIC-OTRA-" + license))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.field").value("documentNumber"));
    }

    private Map<String, Object> professionalBody(String email, String document, String code, String license) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstNames", "Doble");
        body.put("lastNames", "Prueba");
        body.put("documentType", "CC");
        body.put("documentNumber", document);
        body.put("email", email);
        body.put("phone", "3001234567");
        body.put("password", S3TestData.PASSWORD);
        body.put("professionalCode", code);
        body.put("licenseNumber", license);
        body.put("specialtyIds", List.of(general));
        body.put("primarySpecialtyId", general);
        body.put("siteIds", List.of(hic));
        return body;
    }
}
