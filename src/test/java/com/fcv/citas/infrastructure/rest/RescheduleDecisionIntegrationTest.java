package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-031 (aprobar / rechazar una reprogramacion, D22, D23, D18), la lectura de HU-028 (el paciente ve
 * el rechazo y decide), la mitad de reprogramaciones de HU-029 (bandeja, D24) y D37.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RescheduleDecisionIntegrationTest {

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
    private long adminId;
    private String admin;
    private String admin2;
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
        adminId = data.user("admin", "ADMIN");
        admin = tokens.bearer(adminId, Role.ADMIN);
        admin2 = tokens.bearer(data.user("admin2", "ADMIN"), Role.ADMIN);
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

    private long book(String token, String route, long professionalId, int specialtyId, String start)
            throws Exception {
        String body = mvc.perform(post("/api/patient/appointments/" + route)
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("professionalId", professionalId, "siteId", hic,
                        "specialtyId", specialtyId, "date", day.toString(), "startTime", start))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long general(String start) throws Exception {
        return book(patient, "general", gp.id(), general, start);
    }

    private long approvedCardiology(String start) throws Exception {
        long id = book(patient, "specialized", cardiologist.id(), cardiology, start);
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        return id;
    }

    private long reschedule(long appointmentId, int siteId, LocalDate date, String start) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("siteId", siteId);
        body.put("date", date.toString());
        body.put("startTime", start);
        String response = mvc.perform(post("/api/patient/appointments/" + appointmentId + "/reschedule")
                .header(HttpHeaders.AUTHORIZATION, patient).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions approve(long requestId, String token) throws Exception {
        return mvc.perform(post("/api/admin/reschedules/" + requestId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions reject(long requestId, String token, String rawBody) throws Exception {
        var request = post("/api/admin/reschedules/" + requestId + "/reject").header(HttpHeaders.AUTHORIZATION, token);
        if (rawBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(rawBody);
        }
        return mvc.perform(request);
    }

    private ResultActions availability(int specialtyId, LocalDate date) throws Exception {
        return mvc.perform(get("/api/patient/availability?specialtyId=" + specialtyId + "&date=" + date)
                .header(HttpHeaders.AUTHORIZATION, otherPatient));
    }

    private String requestStatus(long requestId) {
        return jdbc.queryForObject("SELECT rs.code FROM reschedule_requests r JOIN reschedule_statuses rs"
                + " ON rs.id = r.status_id WHERE r.id = ?", String.class, requestId);
    }

    private String appointmentStatus(long id) {
        return jdbc.queryForObject("SELECT st.code FROM appointments a JOIN appointment_statuses st"
                + " ON st.id = a.status_id WHERE a.id = ?", String.class, id);
    }

    private int history(long appointmentId) {
        return data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id = ?", appointmentId);
    }

    /** Filas de slot_reservations del slot: tipo, titular y orden (o vacio si esta libre). */
    private Map<String, Object> reservationOf(long blockId, String start) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT reservation_type, appointment_id,"
                + " reschedule_request_id, slot_order, created_at FROM slot_reservations WHERE slot_id = ?",
                data.slotId(blockId, start));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** Todo lo observable de una cita y su solicitud, para afirmar "nada cambio". */
    private String snapshot(long appointmentId, long requestId) {
        return jdbc.queryForList("SELECT a.status_id, a.scheduled_date, a.start_time, a.end_time, a.site_id"
                + " FROM appointments a WHERE a.id = ?", appointmentId)
                + "|" + jdbc.queryForList("SELECT status_id, decided_at, decided_by_user_id, decision_reason"
                        + " FROM reschedule_requests WHERE id = ?", requestId)
                + "|" + jdbc.queryForList("SELECT slot_id, reservation_type, appointment_id, reschedule_request_id,"
                        + " slot_order FROM slot_reservations WHERE appointment_id = ? OR reschedule_request_id = ?"
                        + " ORDER BY slot_id", appointmentId, requestId)
                + "|" + history(appointmentId);
    }

    // ------------------------------------------------------------------ CA-01, CA-02 (60 min)

    /**
     * CA-01 y CA-02 (60 min): aprobar mueve la MISMA cita a 10:00–11:00; los slots 08:00/08:30 quedan
     * libres y se ofrecen; 10:00/10:30 son APPOINTMENT de la cita (orden 1 y 2), sin RESCHEDULE_REQUEST.
     * Las filas nuevas se ACTUALIZAN, no se borran y reinsertan: conservan su created_at.
     */
    @Test
    void approvingMovesTheSameAppointmentAndConvertsTheHeldRows() throws Exception {
        long id = approvedCardiology("08:00");
        long requestId = reschedule(id, hic, day, "10:00");
        Map<String, Object> heldFirst = reservationOf(cardioBlock, "10:00");
        Map<String, Object> heldSecond = reservationOf(cardioBlock, "10:30");
        assertThat(heldFirst.get("reservation_type")).isEqualTo("RESCHEDULE_REQUEST");
        int appointmentsBefore = data.count("SELECT COUNT(*) FROM appointments WHERE patient_user_id = ?", patientId);

        approve(requestId, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.date").value(day.toString()))
                .andExpect(jsonPath("$.startTime").value("10:00"))
                .andExpect(jsonPath("$.endTime").value("11:00"))
                .andExpect(jsonPath("$.site.code").value("HIC"))
                .andExpect(jsonPath("$.professional.id").value(cardiologist.id()))
                .andExpect(jsonPath("$.specialty.id").value(cardiology))
                .andExpect(jsonPath("$.pendingReschedule").value(false))
                .andExpect(jsonPath("$.patient.id").value(patientId))
                .andExpect(jsonPath("$.lastReschedule.id").value(requestId))
                .andExpect(jsonPath("$.lastReschedule.status").value("APPROVED"))
                .andExpect(jsonPath("$.lastReschedule.previous.startTime").value("08:00"))
                .andExpect(jsonPath("$.lastReschedule.proposed.startTime").value("10:00"))
                .andExpect(jsonPath("$.lastReschedule.decidedAt").exists())
                .andExpect(jsonPath("$.cancellable").doesNotExist())
                .andExpect(jsonPath("$.reschedulable").doesNotExist());

        assertThat(data.count("SELECT COUNT(*) FROM appointments WHERE patient_user_id = ?", patientId))
                .isEqualTo(appointmentsBefore);
        Map<String, Object> request = jdbc.queryForMap("SELECT rs.code, r.decided_by_user_id, r.decided_at"
                + " FROM reschedule_requests r JOIN reschedule_statuses rs ON rs.id = r.status_id WHERE r.id = ?",
                requestId);
        assertThat(request.get("code")).isEqualTo("APPROVED");
        assertThat(((Number) request.get("decided_by_user_id")).longValue()).isEqualTo(adminId);
        assertThat(request.get("decided_at")).isNotNull();

        // Fila a fila: antiguas libres, nuevas de la cita con orden 1 y 2, sin retenciones.
        assertThat(reservationOf(cardioBlock, "08:00")).isEmpty();
        assertThat(reservationOf(cardioBlock, "08:30")).isEmpty();
        Map<String, Object> first = reservationOf(cardioBlock, "10:00");
        Map<String, Object> second = reservationOf(cardioBlock, "10:30");
        assertThat(first.get("reservation_type")).isEqualTo("APPOINTMENT");
        assertThat(((Number) first.get("appointment_id")).longValue()).isEqualTo(id);
        assertThat(first.get("reschedule_request_id")).isNull();
        assertThat(((Number) first.get("slot_order")).intValue()).isEqualTo(1);
        assertThat(second.get("reservation_type")).isEqualTo("APPOINTMENT");
        assertThat(((Number) second.get("slot_order")).intValue()).isEqualTo(2);
        assertThat(first.get("created_at")).isNotNull().isEqualTo(heldFirst.get("created_at"));
        assertThat(second.get("created_at")).isNotNull().isEqualTo(heldSecond.get("created_at"));
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE reschedule_request_id = ?", requestId))
                .isZero();
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", id)).isEqualTo(2);

        availability(cardiology, day)
                .andExpect(jsonPath("$[*].startTime", hasItem("08:00")))
                .andExpect(jsonPath("$[*].startTime", not(hasItem("10:00"))));

        // El paciente ve la cita movida y puede volver a pedir (D20).
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.startTime").value("10:00"))
                .andExpect(jsonPath("$.reschedulable").value(true))
                .andExpect(jsonPath("$.lastReschedule.status").value("APPROVED"));
    }

    /** CA-01 (30 min) y D21: aprobar hacia otra sede y otro dia mueve tambien la sede de la cita. */
    @Test
    void approvingThirtyMinutesToAnotherSiteMovesTheSite() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, icv, nextDay, "09:00");

        approve(requestId, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.site.code").value("ICV"))
                .andExpect(jsonPath("$.date").value(nextDay.toString()))
                .andExpect(jsonPath("$.startTime").value("09:00"))
                .andExpect(jsonPath("$.endTime").value("09:30"));

        assertThat(reservationOf(gpBlock, "08:00")).isEmpty();
        Map<String, Object> moved = reservationOf(gpIcvBlock, "09:00");
        assertThat(moved.get("reservation_type")).isEqualTo("APPOINTMENT");
        assertThat(((Number) moved.get("appointment_id")).longValue()).isEqualTo(id);
        assertThat(((Number) jdbc.queryForObject("SELECT site_id FROM appointments WHERE id = ?", Integer.class, id))
                .intValue()).isEqualTo(icv);
    }

    // ------------------------------------------------------------------ CA-03 y HU-028

    /**
     * CA-03 (60 min): rechazar deja la cita intacta y libera la franja propuesta. HU-028 CA-01/CA-04: el
     * paciente ve la solicitud REJECTED con el motivo y su franja vigente; la propuesta vuelve a ofrecerse.
     */
    @Test
    void rejectingKeepsTheAppointmentAndReleasesTheProposal() throws Exception {
        long id = approvedCardiology("08:00");
        long requestId = reschedule(id, hic, day, "10:00");

        reject(requestId, admin, "{\"reason\": \"El especialista no tiene cupo ese día\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.startTime").value("08:00"))
                .andExpect(jsonPath("$.lastReschedule.status").value("REJECTED"))
                .andExpect(jsonPath("$.lastReschedule.decisionReason").value("El especialista no tiene cupo ese día"));

        Map<String, Object> request = jdbc.queryForMap("SELECT rs.code, r.decided_by_user_id, r.decided_at,"
                + " r.decision_reason FROM reschedule_requests r JOIN reschedule_statuses rs ON rs.id = r.status_id"
                + " WHERE r.id = ?", requestId);
        assertThat(request.get("code")).isEqualTo("REJECTED");
        assertThat(((Number) request.get("decided_by_user_id")).longValue()).isEqualTo(adminId);
        assertThat(request.get("decided_at")).isNotNull();
        assertThat(request.get("decision_reason")).isEqualTo("El especialista no tiene cupo ese día");

        assertThat(reservationOf(cardioBlock, "08:00").get("reservation_type")).isEqualTo("APPOINTMENT");
        assertThat(reservationOf(cardioBlock, "08:30").get("reservation_type")).isEqualTo("APPOINTMENT");
        assertThat(reservationOf(cardioBlock, "10:00")).isEmpty();
        assertThat(reservationOf(cardioBlock, "10:30")).isEmpty();
        availability(cardiology, day).andExpect(jsonPath("$[*].startTime", hasItem("10:00")));

        // HU-028 CA-01: el paciente ve el rechazo, la propuesta y su franja vigente; puede volver a pedir.
        int before = history(id);
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.startTime").value("08:00"))
                .andExpect(jsonPath("$.pendingReschedule").value(false))
                .andExpect(jsonPath("$.reschedulable").value(true))
                .andExpect(jsonPath("$.lastReschedule.status").value("REJECTED"))
                .andExpect(jsonPath("$.lastReschedule.statusName").value("Rechazada"))
                .andExpect(jsonPath("$.lastReschedule.proposed.startTime").value("10:00"))
                .andExpect(jsonPath("$.lastReschedule.previous.startTime").value("08:00"))
                .andExpect(jsonPath("$.lastReschedule.decisionReason").value("El especialista no tiene cupo ese día"));
        // HU-028 CA-02: consultar (conservar) no escribe nada.
        assertThat(history(id)).isEqualTo(before);
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", id)).isEqualTo(2);
    }

    /** CA-03 (30 min). */
    @Test
    void rejectingThirtyMinutesReleasesTheHeldSlot() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        reject(requestId, admin, "{\"reason\": \"No\"}").andExpect(status().isOk());
        assertThat(reservationOf(gpBlock, "09:00")).isEmpty();
        assertThat(reservationOf(gpBlock, "08:00").get("reservation_type")).isEqualTo("APPOINTMENT");
        availability(general, day).andExpect(jsonPath("$[*].startTime", hasItem("09:00")));
    }

    /**
     * HU-028 CA-03 y CA-04: tras el rechazo el paciente cancela; la cita queda CANCELLED, su franja
     * original se libera, hay historial USER y ninguna retencion de la solicitud rechazada.
     */
    @Test
    void cancellingAfterARejectionReleasesTheOriginalSlot() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        reject(requestId, admin, "{\"reason\": \"No\"}").andExpect(status().isOk());

        mvc.perform(post("/api/patient/appointments/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lastReschedule.status").value("REJECTED"));
        Map<String, Object> last = jdbc.queryForMap("SELECT st.code, h.source, h.actor_user_id"
                + " FROM appointment_status_history h JOIN appointment_statuses st ON st.id = h.status_id"
                + " WHERE h.appointment_id = ? ORDER BY h.id DESC LIMIT 1", id);
        assertThat(last.get("code")).isEqualTo("CANCELLED");
        assertThat(last.get("source")).isEqualTo("USER");
        assertThat(((Number) last.get("actor_user_id")).longValue()).isEqualTo(patientId);
        assertThat(requestStatus(requestId)).isEqualTo("REJECTED");
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?"
                + " OR reschedule_request_id = ?", id, requestId)).isZero();
        availability(general, day).andExpect(jsonPath("$[*].startTime", hasItem("08:00")))
                .andExpect(jsonPath("$[*].startTime", hasItem("09:00")));
    }

    /** HU-028 CA-05: la cita ya pasada con reprogramacion rechazada no se cancela (APPOINTMENT_EXPIRED). */
    @Test
    void aPastAppointmentWithARejectedRescheduleCannotBeCancelled() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long id = data.appointment(patientId, gp.id(), hic, general, yesterday, LocalTime.of(8, 0),
                LocalTime.of(8, 30), "APPROVED");
        jdbc.update("""
                INSERT INTO reschedule_requests (appointment_id, status_id, requested_by_user_id,
                    previous_date, previous_start_time, previous_end_time, previous_site_id,
                    proposed_date, proposed_start_time, proposed_end_time, proposed_site_id,
                    decided_by_user_id, decided_at, decision_reason)
                VALUES (?, (SELECT id FROM reschedule_statuses WHERE code = 'REJECTED'), ?, ?, '08:00', '08:30', ?,
                        ?, '09:00', '09:30', ?, ?, CURRENT_TIMESTAMP(6), 'No')
                """, id, patientId, yesterday, hic, day, hic, adminId);
        mvc.perform(post("/api/patient/appointments/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_EXPIRED"));
        assertThat(appointmentStatus(id)).isEqualTo("APPROVED");
    }

    /** HU-028 CA-06: la cita ajena con rechazo → 404, sin exponer el motivo. */
    @Test
    void anotherPatientCannotSeeTheRejection() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        reject(requestId, admin, "{\"reason\": \"Motivo privado\"}").andExpect(status().isOk());
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, otherPatient))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.lastReschedule").doesNotExist());
    }

    // ------------------------------------------------------------------ CA-04

    /** CA-04 (RN-04): sin cuerpo, sin motivo o con motivo en blanco → 400; nada cambia. */
    @Test
    void rejectingWithoutAReasonIsAValidationError() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        String before = snapshot(id, requestId);
        reject(requestId, admin, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        reject(requestId, admin, "{}").andExpect(status().isBadRequest());
        reject(requestId, admin, "{\"reason\": \"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        assertThat(snapshot(id, requestId)).isEqualTo(before);
        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------ CA-05

    /** CA-05 (RN-11): decidir una solicitud ya APPROVED o REJECTED → 409 INVALID_TRANSITION, nada cambia. */
    @Test
    void anAlreadyDecidedRequestCannotBeDecidedAgain() throws Exception {
        long approvedId = general("08:00");
        long approvedRequest = reschedule(approvedId, hic, day, "09:00");
        approve(approvedRequest, admin).andExpect(status().isOk());
        long rejectedId = general("10:00");
        long rejectedRequest = reschedule(rejectedId, hic, day, "11:00");
        reject(rejectedRequest, admin, "{\"reason\": \"No\"}").andExpect(status().isOk());

        for (long[] pair : new long[][] { { approvedId, approvedRequest }, { rejectedId, rejectedRequest } }) {
            String before = snapshot(pair[0], pair[1]);
            approve(pair[1], admin).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
            reject(pair[1], admin, "{\"reason\": \"otra vez\"}").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
            assertThat(snapshot(pair[0], pair[1])).isEqualTo(before);
        }
    }

    /** CA-05: una PENDING cuya cita ya no esta APPROVED (p. ej. cerrada) → 409 al aprobar y al rechazar. */
    @Test
    void aPendingRequestOnANonApprovedAppointmentCannotBeDecided() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        jdbc.update("UPDATE appointments SET status_id = (SELECT id FROM appointment_statuses WHERE code = 'COMPLETED')"
                + " WHERE id = ?", id);
        String before = snapshot(id, requestId);
        approve(requestId, admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        reject(requestId, admin, "{\"reason\": \"No\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertThat(snapshot(id, requestId)).isEqualTo(before);
    }

    /** Solicitud inexistente → 404. */
    @Test
    void anUnknownRequestIsNotFound() throws Exception {
        approve(Long.MAX_VALUE, admin).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        reject(Long.MAX_VALUE, admin, "{\"reason\": \"x\"}").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ CA-06

    /**
     * CA-06 (RF-19, D22): cada decision deja exactamente UNA fila de historial APPROVED, origen ADMIN,
     * con el administrador como actor; al aprobar el motivo nombra la franja anterior y la nueva; al
     * rechazar contiene el motivo enviado. El paciente ve "Administración" como actor.
     */
    @Test
    void eachDecisionIsRecordedOnceInTheHistory() throws Exception {
        long moved = general("08:00");
        long kept = general("10:00");
        long approveReq = reschedule(moved, icv, nextDay, "09:00");
        long rejectReq = reschedule(kept, hic, day, "11:00");
        int movedBefore = history(moved);
        int keptBefore = history(kept);

        approve(approveReq, admin).andExpect(status().isOk());
        reject(rejectReq, admin, "{\"reason\": \"Agenda cerrada\"}").andExpect(status().isOk());

        assertThat(history(moved)).isEqualTo(movedBefore + 1);
        assertThat(history(kept)).isEqualTo(keptBefore + 1);
        Map<String, Object> approval = lastHistory(moved);
        assertThat(approval.get("code")).isEqualTo("APPROVED");
        assertThat(approval.get("source")).isEqualTo("ADMIN");
        assertThat(((Number) approval.get("actor_user_id")).longValue()).isEqualTo(adminId);
        assertThat(approval.get("changed_at")).isNotNull();
        assertThat((String) approval.get("reason")).contains(day.toString()).contains("08:00").contains("HIC")
                .contains(nextDay.toString()).contains("09:00").contains("ICV");
        Map<String, Object> rejection = lastHistory(kept);
        assertThat(rejection.get("code")).isEqualTo("APPROVED");
        assertThat(rejection.get("source")).isEqualTo("ADMIN");
        assertThat(((Number) rejection.get("actor_user_id")).longValue()).isEqualTo(adminId);
        assertThat((String) rejection.get("reason")).contains("Agenda cerrada");

        mvc.perform(get("/api/patient/appointments/" + kept).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.history[" + keptBefore + "].actorName").value("Administración"))
                .andExpect(jsonPath("$.history[" + keptBefore + "].reason", containsString("Agenda cerrada")));
        mvc.perform(get("/api/admin/appointments/" + moved).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.history[" + movedBefore + "].source").value("ADMIN"))
                .andExpect(jsonPath("$.lastReschedule.status").value("APPROVED"));
    }

    private Map<String, Object> lastHistory(long appointmentId) {
        return jdbc.queryForMap("SELECT st.code, h.source, h.actor_user_id, h.reason, h.changed_at"
                + " FROM appointment_status_history h JOIN appointment_statuses st ON st.id = h.status_id"
                + " WHERE h.appointment_id = ? ORDER BY h.id DESC LIMIT 1", appointmentId);
    }

    // ------------------------------------------------------------------ CA-07

    /** CA-07: dos ADMIN deciden a la vez: exactamente una decision gana y la otra recibe 409. */
    @Test
    void concurrentDecisionsLetExactlyOneWin() throws Exception {
        for (String[] pair : new String[][] { { "approve", "reject" }, { "approve", "approve" }, { "reject", "reject" } }) {
            long id = general("08:00");
            long requestId = reschedule(id, hic, day, "09:00");
            List<Integer> statuses = race(decision(pair[0], requestId, admin), decision(pair[1], requestId, admin2));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(requestStatus(requestId)).isIn("APPROVED", "REJECTED");
            // Estado final coherente: ningun slot con reservas colgando de la solicitud ni duplicadas.
            assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE reschedule_request_id = ?",
                    requestId)).isZero();
            assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", id)).isEqualTo(1);
            assertThat(data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id = ?"
                    + " AND source = 'ADMIN'", id)).isEqualTo(1);
            mvc.perform(post("/api/patient/appointments/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, patient))
                    .andExpect(status().isOk());
        }
    }

    /**
     * CA-07 ajustado a D18: aprobacion y cancelacion simultaneas se serializan. Si la cancelacion va
     * primero, la solicitud queda CANCELLED y la aprobacion recibe 409; si la aprobacion va primero, la
     * cancelacion actua sobre la cita movida. En ambos ordenes: cita CANCELLED y NINGUNA reserva.
     */
    @Test
    void approvalAndCancellationRacingEndInACoherentState() throws Exception {
        for (int round = 0; round < 4; round++) {
            long id = general(round % 2 == 0 ? "08:00" : "10:00");
            long requestId = reschedule(id, hic, day, round % 2 == 0 ? "09:00" : "11:00");
            Callable<Integer> approval = decision("approve", requestId, admin);
            Callable<Integer> cancel = () -> mvc.perform(post("/api/patient/appointments/" + id + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, patient)).andReturn().getResponse().getStatus();
            List<Integer> statuses = race(approval, cancel);

            assertThat(statuses.get(1)).as("la cancelacion siempre se aplica").isEqualTo(200);
            assertThat(appointmentStatus(id)).isEqualTo("CANCELLED");
            if (statuses.get(0) == 200) {
                assertThat(requestStatus(requestId)).isEqualTo("APPROVED");
            } else {
                assertThat(statuses.get(0)).isEqualTo(409);
                assertThat(requestStatus(requestId)).isEqualTo("CANCELLED");
            }
            assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?"
                    + " OR reschedule_request_id = ?", id, requestId)).as("ninguna reserva huerfana").isZero();
        }
    }

    private Callable<Integer> decision(String kind, long requestId, String token) {
        return () -> ("approve".equals(kind) ? approve(requestId, token)
                : reject(requestId, token, "{\"reason\": \"simultánea\"}")).andReturn().getResponse().getStatus();
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> c : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return c.call();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ RN-01 durante la conversion

    /**
     * HU-031 (RN-01, "no abrir hueco"): mientras el ADMIN aprueba, otra reserva por la franja NUEVA recibe
     * 409 SLOT_TAKEN tanto justo antes de convertir las filas (ya liberadas las antiguas) como justo
     * despues (fila convertida, sin confirmar). Al terminar, el slot es de la cita, una sola fila.
     */
    @Test
    void approvingNeverOpensAGapOnTheNewSlot() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        String booking = json.writeValueAsString(Map.of("professionalId", gp.id(), "siteId", hic,
                "specialtyId", general, "date", day.toString(), "startTime", "09:00"));
        Callable<String> contender = () -> {
            var r = mvc.perform(post("/api/patient/appointments/general").header(HttpHeaders.AUTHORIZATION, otherPatient)
                    .contentType(MediaType.APPLICATION_JSON).content(booking)).andReturn().getResponse();
            return r.getStatus() + " " + r.getContentAsString();
        };
        // Los contendientes corren en otro hilo y no se esperan dentro de la transaccion del ADMIN: un
        // bloqueo de InnoDB podria hacerles esperar a que confirme, y esperarlos aqui colgaria la prueba.
        java.util.function.Supplier<String> attempt = () -> {
            try {
                return contender.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        List<CompletableFuture<String>> contenders = new ArrayList<>();
        doAnswer(invocation -> {
            // Antiguas ya liberadas, filas nuevas aun RESCHEDULE_REQUEST: otra reserva debe chocar.
            contenders.add(CompletableFuture.supplyAsync(attempt));
            Thread.sleep(300);
            Object result = invocation.callRealMethod();
            // Filas ya convertidas pero sin confirmar: la reserva espera el bloqueo y despues choca.
            contenders.add(CompletableFuture.supplyAsync(attempt));
            Thread.sleep(300);
            return result;
        }).when(appointmentRepository).convertHeldToAppointment(anyLong(), anyLong());

        approve(requestId, admin).andExpect(status().isOk()).andExpect(jsonPath("$.startTime").value("09:00"));

        assertThat(contenders).hasSize(2);
        for (CompletableFuture<String> c : contenders) {
            assertThat(c.get(60, TimeUnit.SECONDS)).startsWith("409").contains("SLOT_TAKEN");
        }
        Map<String, Object> row = reservationOf(gpBlock, "09:00");
        assertThat(row.get("reservation_type")).isEqualTo("APPOINTMENT");
        assertThat(((Number) row.get("appointment_id")).longValue()).isEqualTo(id);
        assertThat(data.count("SELECT COUNT(*) FROM appointments WHERE patient_user_id = ?", otherPatientId)).isZero();
    }

    /** DoD: si la conversion falla, nada cambia (solicitud, cita, reservas, historial). */
    @Test
    void aFailureDuringApprovalRollsBackEverything() throws Exception {
        long id = approvedCardiology("08:00");
        long requestId = reschedule(id, hic, day, "10:00");
        String before = snapshot(id, requestId);
        doThrow(new IllegalStateException("fallo simulado de persistencia"))
                .when(appointmentRepository).convertHeldToAppointment(anyLong(), anyLong());

        approve(requestId, admin).andExpect(status().isInternalServerError());

        assertThat(snapshot(id, requestId)).isEqualTo(before);
        assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------ CA-08

    /** CA-08: USER (incluido el titular) y PROFESSIONAL → 403; anonimo → 401; nada cambia. */
    @Test
    void onlyAdminDecides() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        String before = snapshot(id, requestId);
        String professional = tokens.bearer(gp.userId(), Role.PROFESSIONAL);
        for (String token : new String[] { patient, professional }) {
            approve(requestId, token).andExpect(status().isForbidden());
            reject(requestId, token, "{\"reason\": \"x\"}").andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/reschedules/" + requestId + "/approve")).andExpect(status().isUnauthorized());
        assertThat(snapshot(id, requestId)).isEqualTo(before);
    }

    // ------------------------------------------------------------------ CA-09 (D23)

    /**
     * CA-09 (D23, RN-06): una PENDING cuya franja propuesta ya empezo no se aprueba (409
     * APPOINTMENT_EXPIRED, nada cambia); rechazarla con motivo si funciona y libera la retencion.
     */
    @Test
    void aRequestWhoseProposedSlotAlreadyPassedCannotBeApproved() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long pastBlock = data.block(gp.id(), hic, yesterday, "08:00", "09:00");
        long id = general("08:00");
        jdbc.update("""
                INSERT INTO reschedule_requests (appointment_id, status_id, requested_by_user_id,
                    previous_date, previous_start_time, previous_end_time, previous_site_id,
                    proposed_date, proposed_start_time, proposed_end_time, proposed_site_id)
                VALUES (?, (SELECT id FROM reschedule_statuses WHERE code = 'PENDING'), ?, ?, '08:00', '08:30', ?,
                        ?, '08:00', '08:30', ?)
                """, id, patientId, day, hic, yesterday, hic);
        long requestId = jdbc.queryForObject("SELECT id FROM reschedule_requests WHERE appointment_id = ?", Long.class, id);
        jdbc.update("INSERT INTO slot_reservations (slot_id, reservation_type, reschedule_request_id, slot_order)"
                + " VALUES (?, 'RESCHEDULE_REQUEST', ?, 1)", data.slotId(pastBlock, "08:00"), requestId);
        String before = snapshot(id, requestId);

        approve(requestId, admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_EXPIRED"));
        assertThat(snapshot(id, requestId)).isEqualTo(before);

        reject(requestId, admin, "{\"reason\": \"La franja propuesta ya pasó\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("08:00"))
                .andExpect(jsonPath("$.date").value(day.toString()));
        assertThat(requestStatus(requestId)).isEqualTo("REJECTED");
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE reschedule_request_id = ?", requestId))
                .isZero();
    }

    // ------------------------------------------------------------------ D18 + D37

    /** D18 y D37: cancelar la cita con una PENDING la cierra con decisor paciente y motivo automatico. */
    @Test
    void cancellingWithAPendingRequestClosesItWithTheAutomaticReason() throws Exception {
        long id = general("08:00");
        long requestId = reschedule(id, hic, day, "09:00");
        mvc.perform(post("/api/patient/appointments/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReschedule.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lastReschedule.decisionReason").value("Cita cancelada por el paciente"));
        Map<String, Object> request = jdbc.queryForMap("SELECT rs.code, r.decided_by_user_id, r.decision_reason"
                + " FROM reschedule_requests r JOIN reschedule_statuses rs ON rs.id = r.status_id WHERE r.id = ?",
                requestId);
        assertThat(request.get("code")).isEqualTo("CANCELLED");
        assertThat(((Number) request.get("decided_by_user_id")).longValue()).isEqualTo(patientId);
        assertThat(request.get("decision_reason")).isEqualTo("Cita cancelada por el paciente");
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?"
                + " OR reschedule_request_id = ?", id, requestId)).isZero();
    }

    // ------------------------------------------------------------------ HU-029 (mitad de reprogramaciones)

    /**
     * HU-029 CA-01 y CA-04: la bandeja lista las especializadas REQUESTED y SOLO las reprogramaciones
     * PENDING (no APPROVED ni REJECTED), cada una con su tipo; la reprogramacion trae la cita (franja
     * actual) y la solicitud con franja anterior y propuesta. CA-05: una decidida sale de la bandeja.
     */
    @Test
    void theInboxListsPendingReschedulesWithBothSlots() throws Exception {
        long requested = book(otherPatient, "specialized", cardiologist.id(), cardiology, "08:00");
        long pendingAppt = general("08:00");
        long pending = reschedule(pendingAppt, icv, nextDay, "09:00");
        long approvedAppt = general("10:00");
        approve(reschedule(approvedAppt, hic, day, "11:00"), admin).andExpect(status().isOk());
        long rejectedAppt = general("08:30");
        reject(reschedule(rejectedAppt, hic, day, "09:30"), admin, "{\"reason\": \"No\"}").andExpect(status().isOk());

        String body = mvc.perform(get("/api/admin/inbox?professionalId=" + gp.id())
                .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("RESCHEDULE_REQUEST"))
                .andExpect(jsonPath("$[0].appointment.id").value(pendingAppt))
                .andExpect(jsonPath("$[0].appointment.startTime").value("08:00"))
                .andExpect(jsonPath("$[0].appointment.site.code").value("HIC"))
                .andExpect(jsonPath("$[0].appointment.durationMinutes").value(30))
                .andExpect(jsonPath("$[0].appointment.professional.id").value(gp.id()))
                .andExpect(jsonPath("$[0].appointment.specialty.id").value(general))
                .andExpect(jsonPath("$[0].appointment.patient.id").value(patientId))
                .andExpect(jsonPath("$[0].appointment.pendingReschedule").value(true))
                .andExpect(jsonPath("$[0].reschedule.id").value(pending))
                .andExpect(jsonPath("$[0].reschedule.status").value("PENDING"))
                .andExpect(jsonPath("$[0].reschedule.previous.startTime").value("08:00"))
                .andExpect(jsonPath("$[0].reschedule.previous.site.code").value("HIC"))
                .andExpect(jsonPath("$[0].reschedule.proposed.startTime").value("09:00"))
                .andExpect(jsonPath("$[0].reschedule.proposed.date").value(nextDay.toString()))
                .andExpect(jsonPath("$[0].reschedule.proposed.site.code").value("ICV"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"id\":" + approvedAppt + ",").doesNotContain("\"id\":" + rejectedAppt + ",");

        // Sin filtro de profesional: estan los dos tipos.
        mvc.perform(get("/api/admin/inbox").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[?(@.type == 'APPOINTMENT_REQUEST')].appointment.id", hasItem((int) requested)))
                .andExpect(jsonPath("$[?(@.type == 'RESCHEDULE_REQUEST')].reschedule.id", hasItem((int) pending)))
                .andExpect(jsonPath("$[?(@.type == 'APPOINTMENT_REQUEST')].reschedule").isEmpty());

        // CA-05: decidida, sale.
        approve(pending, admin).andExpect(status().isOk());
        mvc.perform(get("/api/admin/inbox").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].reschedule.id", not(hasItem((int) pending))));
    }

    /**
     * HU-029 CA-02/CA-03 con D24: sede y fecha se aplican a la franja PROPUESTA; profesional y especialidad
     * a la cita; {@code type} filtra por clase de entrada. Un {@code type} desconocido → 400.
     */
    @Test
    void inboxFiltersApplyToTheProposedSlotAndCombine() throws Exception {
        long appt = general("08:00");
        long pending = reschedule(appt, icv, nextDay, "09:00");
        long requested = book(otherPatient, "specialized", cardiologist.id(), cardiology, "08:00");

        inbox("siteId=" + icv).andExpect(jsonPath("$[*].reschedule.id", hasItem((int) pending)))
                .andExpect(jsonPath("$[*].appointment.id", not(hasItem((int) requested))));
        inbox("siteId=" + hic).andExpect(jsonPath("$[*].reschedule.id", not(hasItem((int) pending))))
                .andExpect(jsonPath("$[*].appointment.id", hasItem((int) requested)));
        inbox("date=" + nextDay).andExpect(jsonPath("$[*].reschedule.id", hasItem((int) pending)));
        inbox("date=" + day + "&professionalId=" + gp.id()).andExpect(jsonPath("$.length()").value(0));
        inbox("siteId=" + icv + "&date=" + nextDay + "&professionalId=" + gp.id() + "&specialtyId=" + general)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reschedule.id").value(pending));
        inbox("specialtyId=" + cardiology + "&professionalId=" + gp.id()).andExpect(jsonPath("$.length()").value(0));
        inbox("type=RESCHEDULE_REQUEST").andExpect(jsonPath("$[*].type", not(hasItem("APPOINTMENT_REQUEST"))))
                .andExpect(jsonPath("$[*].reschedule.id", hasItem((int) pending)));
        inbox("type=APPOINTMENT_REQUEST").andExpect(jsonPath("$[*].type", not(hasItem("RESCHEDULE_REQUEST"))))
                .andExpect(jsonPath("$[*].appointment.id", hasItem((int) requested)));
        inbox("type=OTRO").andExpect(status().isBadRequest());
    }

    private ResultActions inbox(String query) throws Exception {
        return mvc.perform(get("/api/admin/inbox?" + query).header(HttpHeaders.AUTHORIZATION, admin));
    }

    /** Contrato S4: summary.pendingReschedules cuenta las PENDING y baja al decidir. */
    @Test
    void theSummaryCountsPendingReschedules() throws Exception {
        long before = json.readTree(mvc.perform(get("/api/admin/summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("pendingReschedules").asLong();
        long requestId = reschedule(general("08:00"), hic, day, "09:00");
        mvc.perform(get("/api/admin/summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.pendingReschedules").value(before + 1));
        reject(requestId, admin, "{\"reason\": \"No\"}").andExpect(status().isOk());
        mvc.perform(get("/api/admin/summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.pendingReschedules").value(before));
    }
}
