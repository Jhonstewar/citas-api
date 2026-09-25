package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-027: el paciente pide reprogramar una cita APPROVED y futura (RF-15, RN-01, RN-05, RN-06, RN-10,
 * D20, D21). Tambien HU-022 CA-03: la busqueda no ofrece franjas retenidas por una reprogramacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RescheduleRequestIntegrationTest {

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
    /** Espia sobre el adaptador real: solo se altera en la prueba de atomicidad. */
    @MockitoSpyBean
    private AppointmentRepository appointmentRepository;

    private S3TestData data;
    private TestTokens tokens;
    private int hic;
    private int icv;
    private int general;
    private int cardiology;
    private S3TestData.Professional gp;
    private S3TestData.Professional cardiologist;
    private long patientId;
    private String patient;
    private long otherPatientId;
    private String otherPatient;
    private String admin;
    private LocalDate day;
    private LocalDate nextDay;
    private long gpBlock;
    private long gpIcvBlock;
    private long cardioBlock;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        icv = data.siteId("ICV");
        general = data.generalMedicineId();
        cardiology = data.specialty("SPECIALIZED", 60);
        gp = data.professional("gp", new int[] { general }, hic, icv);
        cardiologist = data.professional("cardio", new int[] { cardiology }, hic);
        patientId = data.user("patient", "USER");
        patient = tokens.bearer(patientId, Role.USER);
        otherPatientId = data.user("other", "USER");
        otherPatient = tokens.bearer(otherPatientId, Role.USER);
        admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        day = LocalDate.now(SystemZone.ZONE).plusDays(5);
        nextDay = day.plusDays(1);
        gpBlock = data.block(gp.id(), hic, day, "08:00", "12:00");
        gpIcvBlock = data.block(gp.id(), icv, nextDay, "08:00", "10:00");
        cardioBlock = data.block(cardiologist.id(), hic, day, "08:00", "12:00");
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    // ------------------------------------------------------------------ ayudas

    private long book(String token, String route, long professionalId, int specialtyId, int siteId, LocalDate date,
            String start) throws Exception {
        String body = mvc.perform(post("/api/patient/appointments/" + route)
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("professionalId", professionalId, "siteId", siteId,
                        "specialtyId", specialtyId, "date", date.toString(), "startTime", start))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long general(String start) throws Exception {
        return book(patient, "general", gp.id(), general, hic, day, start);
    }

    /** Cita especializada de 60 min ya APPROVED por el ADMIN. */
    private long approvedCardiology(String start) throws Exception {
        long id = book(patient, "specialized", cardiologist.id(), cardiology, hic, day, start);
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions reschedule(long id, String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/patient/appointments/" + id + "/reschedule")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> slot(int siteId, LocalDate date, String start) {
        Map<String, Object> body = new HashMap<>();
        body.put("siteId", siteId);
        body.put("date", date.toString());
        body.put("startTime", start);
        return body;
    }

    private ResultActions availability(int specialtyId, String token) throws Exception {
        return mvc.perform(get("/api/patient/availability?specialtyId=" + specialtyId + "&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private int requests(long appointmentId) {
        return data.count("SELECT COUNT(*) FROM reschedule_requests WHERE appointment_id = ?", appointmentId);
    }

    private int heldSlots() {
        return data.count("SELECT COUNT(*) FROM slot_reservations r JOIN reschedule_requests q"
                + " ON q.id = r.reschedule_request_id JOIN appointments a ON a.id = q.appointment_id"
                + " WHERE a.patient_user_id IN (?, ?)", patientId, otherPatientId);
    }

    private String holderOf(long blockId, String start) {
        List<String> rows = jdbc.queryForList("SELECT r.reservation_type FROM slot_reservations r"
                + " WHERE r.slot_id = ?", String.class, data.slotId(blockId, start));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int history(long appointmentId) {
        return data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id = ?", appointmentId);
    }

    // ------------------------------------------------------------------ CA-03, CA-05, CA-02 (caso valido)

    /**
     * CA-03 y CA-05 (RN-10): la solicitud nace PENDING con la franja anterior y la propuesta; la cita
     * sigue APPROVED en su franja y las DOS franjas quedan ocupadas a la vez. CA-02: conserva profesional
     * y especialidad. No escribe historial: la cita no cambia de estado.
     */
    @Test
    void aValidRequestIsPendingAndHoldsBothSlotsAtOnce() throws Exception {
        long id = general("08:00");
        int before = history(id);
        Map<String, Object> body = slot(hic, day, "10:00");
        body.put("reason", "Tengo un examen a esa hora");

        String response = reschedule(id, patient, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.appointmentId").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusName").value("Pendiente"))
                .andExpect(jsonPath("$.previous.date").value(day.toString()))
                .andExpect(jsonPath("$.previous.startTime").value("08:00"))
                .andExpect(jsonPath("$.previous.endTime").value("08:30"))
                .andExpect(jsonPath("$.previous.site.code").value("HIC"))
                .andExpect(jsonPath("$.proposed.date").value(day.toString()))
                .andExpect(jsonPath("$.proposed.startTime").value("10:00"))
                .andExpect(jsonPath("$.proposed.endTime").value("10:30"))
                .andExpect(jsonPath("$.proposed.site.id").value(hic))
                .andExpect(jsonPath("$.requestReason").value("Tengo un examen a esa hora"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.decisionReason").doesNotExist())
                .andExpect(jsonPath("$.decidedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(response).get("id").asLong();

        Map<String, Object> row = jdbc.queryForMap("SELECT rs.code, r.previous_date, r.previous_start_time,"
                + " r.previous_end_time, r.previous_site_id, r.proposed_date, r.proposed_start_time,"
                + " r.proposed_end_time, r.proposed_site_id, r.requested_by_user_id, r.decided_at"
                + " FROM reschedule_requests r JOIN reschedule_statuses rs ON rs.id = r.status_id WHERE r.id = ?",
                requestId);
        assertThat(row.get("code")).isEqualTo("PENDING");
        assertThat(row.get("previous_date").toString()).isEqualTo(day.toString());
        assertThat(row.get("previous_start_time").toString()).startsWith("08:00");
        assertThat(row.get("previous_end_time").toString()).startsWith("08:30");
        assertThat(((Number) row.get("previous_site_id")).intValue()).isEqualTo(hic);
        assertThat(row.get("proposed_start_time").toString()).startsWith("10:00");
        assertThat(((Number) row.get("proposed_site_id")).intValue()).isEqualTo(hic);
        assertThat(((Number) row.get("requested_by_user_id")).longValue()).isEqualTo(patientId);
        assertThat(row.get("decided_at")).isNull();

        // RN-10: la cita no cambia y ambas franjas estan ocupadas simultaneamente.
        Map<String, Object> appointment = jdbc.queryForMap("SELECT st.code, a.scheduled_date, a.start_time,"
                + " a.professional_id, a.specialty_id FROM appointments a JOIN appointment_statuses st"
                + " ON st.id = a.status_id WHERE a.id = ?", id);
        assertThat(appointment.get("code")).isEqualTo("APPROVED");
        assertThat(appointment.get("start_time").toString()).startsWith("08:00");
        assertThat(((Number) appointment.get("professional_id")).longValue()).isEqualTo(gp.id());
        assertThat(((Number) appointment.get("specialty_id")).intValue()).isEqualTo(general);
        assertThat(holderOf(gpBlock, "08:00")).isEqualTo("APPOINTMENT");
        assertThat(holderOf(gpBlock, "10:00")).isEqualTo("RESCHEDULE_REQUEST");
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE reschedule_request_id = ?", requestId))
                .isEqualTo(1);
        assertThat(history(id)).isEqualTo(before);

        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.startTime").value("08:00"))
                .andExpect(jsonPath("$.pendingReschedule").value(true))
                .andExpect(jsonPath("$.reschedulable").value(false))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.lastReschedule.id").value(requestId))
                .andExpect(jsonPath("$.lastReschedule.status").value("PENDING"))
                .andExpect(jsonPath("$.lastReschedule.proposed.startTime").value("10:00"));
        mvc.perform(get("/api/patient/appointments").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$[0].pendingReschedule").value(true));
    }

    /** D21: la franja nueva puede estar en otra sede si el profesional atiende en ella. */
    @Test
    void theProposedSlotMayBeInAnotherSiteOfTheSameProfessional() throws Exception {
        long id = general("08:00");
        reschedule(id, patient, slot(icv, nextDay, "09:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previous.site.code").value("HIC"))
                .andExpect(jsonPath("$.proposed.site.code").value("ICV"))
                .andExpect(jsonPath("$.proposed.date").value(nextDay.toString()));
        assertThat(holderOf(gpIcvBlock, "09:00")).isEqualTo("RESCHEDULE_REQUEST");
    }

    /** D21 / RN-07: una sede donde el profesional no atiende → 422 SITE_NOT_ASSIGNED; nada se crea. */
    @Test
    void aSiteWhereTheProfessionalDoesNotWorkIsRejected() throws Exception {
        long id = approvedCardiology("08:00");
        reschedule(id, patient, slot(icv, day, "10:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SITE_NOT_ASSIGNED"));
        assertThat(requests(id)).isZero();
    }

    // ------------------------------------------------------------------ CA-02

    /** CA-02 (RF-15): cambiar de profesional o de especialidad es una cita nueva → 422 WRONG_FLOW. */
    @Test
    void changingProfessionalOrSpecialtyIsRejectedAsANewAppointment() throws Exception {
        long id = general("08:00");
        Map<String, Object> otherProfessional = slot(hic, day, "08:00");
        otherProfessional.put("startTime", "10:00");
        otherProfessional.put("professionalId", cardiologist.id());
        reschedule(id, patient, otherProfessional)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_FLOW"))
                .andExpect(jsonPath("$.detail").exists());
        Map<String, Object> otherSpecialty = slot(hic, day, "10:00");
        otherSpecialty.put("specialtyId", cardiology);
        reschedule(id, patient, otherSpecialty)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_FLOW"));
        assertThat(requests(id)).isZero();
        assertThat(heldSlots()).isZero();

        // Enviar el MISMO profesional y especialidad es admisible (el cuerpo no los necesita).
        Map<String, Object> same = slot(hic, day, "10:00");
        same.put("professionalId", gp.id());
        same.put("specialtyId", general);
        reschedule(id, patient, same).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ CA-01

    /** CA-01: REQUESTED, CANCELLED, REJECTED y COMPLETED → 409 INVALID_TRANSITION; APPROVED pasada → APPOINTMENT_EXPIRED. */
    @Test
    void onlyAnApprovedFutureAppointmentAdmitsARequest() throws Exception {
        for (String state : new String[] { "REQUESTED", "CANCELLED", "REJECTED", "COMPLETED", "NO_SHOW" }) {
            long id = data.appointment(patientId, gp.id(), hic, general, day, LocalTime.of(11, 0),
                    LocalTime.of(11, 30), state);
            reschedule(id, patient, slot(hic, day, "10:00"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
            assertThat(requests(id)).isZero();
            mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                    .andExpect(jsonPath("$.reschedulable").value(false));
        }
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long past = data.appointment(patientId, gp.id(), hic, general, yesterday, LocalTime.of(8, 0),
                LocalTime.of(8, 30), "APPROVED");
        reschedule(past, patient, slot(hic, day, "10:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_EXPIRED"));
        assertThat(requests(past)).isZero();
        assertThat(heldSlots()).isZero();
        assertThat(holderOf(gpBlock, "10:00")).isNull();
    }

    // ------------------------------------------------------------------ CA-04 y HU-022 CA-03

    /**
     * CA-04 y HU-022 CA-03: una franja retenida por una reprogramacion PENDING no se ofrece y reservarla
     * directamente da 409 SLOT_TAKEN; tampoco se ofrece la retenida por una cita REQUESTED.
     */
    @Test
    void heldSlotsAreNeitherOfferedNorBookable() throws Exception {
        // Retencion REQUESTED (otro paciente) sobre 08:00–09:00 del cardiologo.
        book(otherPatient, "specialized", cardiologist.id(), cardiology, hic, day, "08:00");
        // Cita APPROVED a las 10:00 que pide pasar a 09:00–10:00: retencion RESCHEDULE_REQUEST.
        long id = approvedCardiology("10:00");
        availability(cardiology, otherPatient).andExpect(jsonPath("$[*].startTime", hasItem("09:00")));
        reschedule(id, patient, slot(hic, day, "09:00")).andExpect(status().isCreated());

        availability(cardiology, otherPatient)
                .andExpect(jsonPath("$[*].startTime", not(hasItem("08:00"))))
                .andExpect(jsonPath("$[*].startTime", not(hasItem("09:00"))))
                .andExpect(jsonPath("$[*].startTime", not(hasItem("09:30"))))
                .andExpect(jsonPath("$[*].startTime", not(hasItem("10:00"))))
                .andExpect(jsonPath("$[*].startTime", hasItem("11:00")));

        mvc.perform(post("/api/patient/appointments/specialized").header(HttpHeaders.AUTHORIZATION, otherPatient)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("professionalId", cardiologist.id(), "siteId", hic,
                        "specialtyId", cardiology, "date", day.toString(), "startTime", "09:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
        assertThat(holderOf(cardioBlock, "09:00")).isEqualTo("RESCHEDULE_REQUEST");
        assertThat(holderOf(cardioBlock, "09:30")).isEqualTo("RESCHEDULE_REQUEST");
    }

    // ------------------------------------------------------------------ CA-06

    /** CA-06 (RN-06): franja propuesta en el pasado → 422 PAST_TIME, sin solicitud ni retencion. */
    @Test
    void aProposedSlotInThePastIsRejected() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long pastBlock = data.block(gp.id(), hic, yesterday, "08:00", "10:00");
        long id = general("08:00");
        reschedule(id, patient, slot(hic, yesterday, "09:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAST_TIME"));
        assertThat(requests(id)).isZero();
        assertThat(holderOf(pastBlock, "09:00")).isNull();
    }

    // ------------------------------------------------------------------ CA-07

    /**
     * CA-07 (RN-05, D9): 60 min sin slot siguiente → 422 SLOT_NOT_AVAILABLE; con el siguiente ocupado →
     * 409 SLOT_TAKEN sin retener el primero; con los dos libres se retienen ambos (orden 1 y 2).
     */
    @Test
    void sixtyMinutesNeedsTwoConsecutiveFreeSlots() throws Exception {
        long id = approvedCardiology("08:00");
        reschedule(id, patient, slot(hic, day, "11:30"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
        assertThat(holderOf(cardioBlock, "11:30")).isNull();

        long taken = data.appointment(otherPatientId, cardiologist.id(), hic, cardiology, day, LocalTime.of(11, 0),
                LocalTime.of(12, 0), "APPROVED");
        data.reserve(data.slotId(cardioBlock, "11:00"), taken, 1);
        reschedule(id, patient, slot(hic, day, "10:30"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
        assertThat(holderOf(cardioBlock, "10:30")).isNull();
        assertThat(requests(id)).isZero();

        String response = reschedule(id, patient, slot(hic, day, "09:30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposed.endTime").value("10:30"))
                .andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(response).get("id").asLong();
        List<Map<String, Object>> held = jdbc.queryForList("SELECT s.start_time, r.slot_order FROM slot_reservations r"
                + " JOIN availability_slots s ON s.id = r.slot_id WHERE r.reschedule_request_id = ?"
                + " ORDER BY r.slot_order", requestId);
        assertThat(held).hasSize(2);
        assertThat(held.get(0).get("start_time").toString()).startsWith("09:30");
        assertThat(((Number) held.get(0).get("slot_order")).intValue()).isEqualTo(1);
        assertThat(held.get(1).get("start_time").toString()).startsWith("10:00");
        assertThat(((Number) held.get(1).get("slot_order")).intValue()).isEqualTo(2);
    }

    /** La franja propuesta que se cruza con la actual (60 min, media hora despues) → 422, sin retencion. */
    @Test
    void aProposalOverlappingTheCurrentSlotIsRejected() throws Exception {
        long id = approvedCardiology("08:00");
        reschedule(id, patient, slot(hic, day, "08:30"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
        assertThat(requests(id)).isZero();
    }

    /** SAME_SLOT: proponer exactamente la franja actual → 422. */
    @Test
    void theSameSlotIsRejected() throws Exception {
        long id = general("08:00");
        reschedule(id, patient, slot(hic, day, "08:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_SLOT"));
        assertThat(requests(id)).isZero();
    }

    // ------------------------------------------------------------------ CA-08

    /** CA-08 (RN-01): franja ocupada por una APPROVED o retenida por una REQUESTED → 409 SLOT_TAKEN. */
    @Test
    void aTakenOrRetainedSlotCannotBeProposed() throws Exception {
        long id = approvedCardiology("08:00");
        book(otherPatient, "specialized", cardiologist.id(), cardiology, hic, day, "10:00"); // REQUESTED
        long approved = approvedCardiologyFor(otherPatient, "11:00");
        reschedule(id, patient, slot(hic, day, "10:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
        reschedule(id, patient, slot(hic, day, "11:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
        assertThat(requests(id)).isZero();
        assertThat(heldSlots()).isZero();
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", approved))
                .isEqualTo(2);
    }

    private long approvedCardiologyFor(String token, String start) throws Exception {
        long id = book(token, "specialized", cardiologist.id(), cardiology, hic, day, start);
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        return id;
    }

    // ------------------------------------------------------------------ CA-10

    /**
     * CA-10 (D20): con una PENDING, otra solicitud → 409 RESCHEDULE_PENDING sin retener nada; tras la
     * decision del ADMIN (rechazo y despues aprobacion) se admite una nueva.
     */
    @Test
    void onlyOneUndecidedRequestPerAppointment() throws Exception {
        long id = general("08:00");
        long first = requestId(reschedule(id, patient, slot(hic, day, "10:00")).andExpect(status().isCreated()));
        reschedule(id, patient, slot(hic, day, "11:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESCHEDULE_PENDING"));
        assertThat(requests(id)).isEqualTo(1);
        assertThat(holderOf(gpBlock, "11:00")).isNull();

        mvc.perform(post("/api/admin/reschedules/" + first + "/reject").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Sin cupo\"}"))
                .andExpect(status().isOk());
        long second = requestId(reschedule(id, patient, slot(hic, day, "11:00")).andExpect(status().isCreated()));
        mvc.perform(post("/api/admin/reschedules/" + second + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        reschedule(id, patient, slot(hic, day, "09:00")).andExpect(status().isCreated());
        assertThat(requests(id)).isEqualTo(3);
    }

    private long requestId(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    // ------------------------------------------------------------------ ownership, rol y validacion

    /** HU-005: la cita ajena o inexistente → 404; solo USER pide (PROFESSIONAL/ADMIN 403, anonimo 401). */
    @Test
    void ownershipAndRoleAreEnforced() throws Exception {
        long id = general("08:00");
        reschedule(id, otherPatient, slot(hic, day, "10:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        reschedule(Long.MAX_VALUE, patient, slot(hic, day, "10:00"))
                .andExpect(status().isNotFound());
        reschedule(id, tokens.bearer(gp.userId(), Role.PROFESSIONAL), slot(hic, day, "10:00"))
                .andExpect(status().isForbidden());
        reschedule(id, admin, slot(hic, day, "10:00")).andExpect(status().isForbidden());
        mvc.perform(post("/api/patient/appointments/" + id + "/reschedule").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(slot(hic, day, "10:00")))).andExpect(status().isUnauthorized());
        assertThat(requests(id)).isZero();
    }

    /** Campos obligatorios y motivo ≤ 500 → 400 con fieldErrors; nada se crea. */
    @Test
    void missingFieldsAndLongReasonsAreValidationErrors() throws Exception {
        long id = general("08:00");
        reschedule(id, patient, Map.of("date", day.toString(), "startTime", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.siteId").exists());
        reschedule(id, patient, Map.of("siteId", hic, "startTime", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").exists());
        reschedule(id, patient, Map.of("siteId", hic, "date", day.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists());
        Map<String, Object> longReason = slot(hic, day, "10:00");
        longReason.put("reason", "x".repeat(501));
        reschedule(id, patient, longReason)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        assertThat(requests(id)).isZero();
        assertThat(heldSlots()).isZero();
    }

    // ------------------------------------------------------------------ DoD: una transaccion, misma barrera

    /** DoD: si la retencion falla, no queda solicitud creada (sin resultados parciales). */
    @Test
    void aFailureHoldingTheSlotRollsBackTheRequest() throws Exception {
        long id = general("08:00");
        doThrow(new IllegalStateException("fallo simulado de persistencia"))
                .when(appointmentRepository).holdForReschedule(anyLong(), anyList());

        reschedule(id, patient, slot(hic, day, "10:00")).andExpect(status().isInternalServerError());

        assertThat(requests(id)).isZero();
        assertThat(holderOf(gpBlock, "10:00")).isNull();
    }

    /**
     * DoD (RN-01 frente a concurrencia): una solicitud de reprogramacion y una reserva normal compiten por
     * la misma franja. Exactamente una gana; la otra recibe 409 SLOT_TAKEN y el slot tiene UNA fila.
     */
    @Test
    void aRequestAndABookingRacingForTheSameSlotLetExactlyOneWin() throws Exception {
        String[] starts = { "09:00", "09:30", "10:00", "10:30", "11:00" };
        long id = general("08:00");
        for (String start : starts) {
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Callable<int[]> requestRace = () -> {
                    go.await();
                    var r = reschedule(id, patient, slot(hic, day, start)).andReturn().getResponse();
                    return new int[] { r.getStatus(), r.getContentAsString().contains("SLOT_TAKEN") ? 1 : 0 };
                };
                Callable<int[]> bookingRace = () -> {
                    go.await();
                    var r = mvc.perform(post("/api/patient/appointments/general")
                            .header(HttpHeaders.AUTHORIZATION, otherPatient).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("professionalId", gp.id(), "siteId", hic,
                                    "specialtyId", general, "date", day.toString(), "startTime", start))))
                            .andReturn().getResponse();
                    return new int[] { r.getStatus(), r.getContentAsString().contains("SLOT_TAKEN") ? 1 : 0 };
                };
                List<Future<int[]>> futures = new ArrayList<>();
                futures.add(pool.submit(requestRace));
                futures.add(pool.submit(bookingRace));
                go.countDown();
                int[] a = futures.get(0).get();
                int[] b = futures.get(1).get();
                List<Integer> statuses = List.of(a[0], b[0]);
                assertThat(statuses).containsExactlyInAnyOrder(201, 409);
                int[] loser = a[0] == 409 ? a : b;
                assertThat(loser[1]).as("el perdedor recibe SLOT_TAKEN").isEqualTo(1);
                assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE slot_id = ?",
                        data.slotId(gpBlock, start))).isEqualTo(1);
                if (a[0] == 201) {
                    // La solicitud gano: se rechaza para poder pedir otra en la siguiente ronda.
                    long request = jdbc.queryForObject("SELECT id FROM reschedule_requests WHERE appointment_id = ?"
                            + " AND decided_at IS NULL", Long.class, id);
                    mvc.perform(post("/api/admin/reschedules/" + request + "/reject")
                            .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"ronda\"}")).andExpect(status().isOk());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
