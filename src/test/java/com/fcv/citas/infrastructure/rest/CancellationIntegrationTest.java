package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

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
 * HU-026: el paciente cancela una cita futura no terminal. Cubre CA-01 a CA-09, D16 (REQUESTED se
 * cancela), D17 (sin antelacion minima) y D18 (reprogramacion PENDING cancelada con la cita).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancellationIntegrationTest {

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
    /** Espia sobre el adaptador real: solo se altera en la prueba de atomicidad (CA-08). */
    @MockitoSpyBean
    private AppointmentRepository appointmentRepository;

    private S3TestData data;
    private TestTokens tokens;
    private int hic;
    private int general;
    private int cardiology;
    private S3TestData.Professional gp;
    private S3TestData.Professional cardiologist;
    private long patientId;
    private String patient;
    private String otherPatient;
    private String admin;
    private LocalDate day;
    private long gpBlock;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        general = data.generalMedicineId();
        cardiology = data.specialty("SPECIALIZED", 60);
        gp = data.professional("gp", new int[] { general }, hic);
        cardiologist = data.professional("cardio", new int[] { cardiology }, hic);
        patientId = data.user("patient", "USER");
        patient = tokens.bearer(patientId, Role.USER);
        otherPatient = tokens.bearer(data.user("other", "USER"), Role.USER);
        admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        day = LocalDate.now(SystemZone.ZONE).plusDays(5);
        gpBlock = data.block(gp.id(), hic, day, "08:00", "10:00");
        data.block(cardiologist.id(), hic, day, "08:00", "10:00");
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    // ------------------------------------------------------------------ ayudas

    private long book(String route, long professionalId, int specialtyId, String start) throws Exception {
        String body = mvc.perform(post("/api/patient/appointments/" + route)
                .header(HttpHeaders.AUTHORIZATION, patient).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("professionalId", professionalId, "siteId", hic,
                        "specialtyId", specialtyId, "date", day.toString(), "startTime", start))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long general(String start) throws Exception {
        return book("general", gp.id(), general, start);
    }

    private long specialized(String start) throws Exception {
        return book("specialized", cardiologist.id(), cardiology, start);
    }

    private ResultActions cancel(long id, String token, String rawBody) throws Exception {
        var request = post("/api/patient/appointments/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, token);
        if (rawBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(rawBody);
        }
        return mvc.perform(request);
    }

    private ResultActions availability(int specialtyId, String token) throws Exception {
        return mvc.perform(get("/api/patient/availability?specialtyId=" + specialtyId + "&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private int reservations(long appointmentId) {
        return data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", appointmentId);
    }

    private int history(long appointmentId) {
        return data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id = ?", appointmentId);
    }

    private String statusOf(long appointmentId) {
        return jdbc.queryForObject("SELECT st.code FROM appointments a JOIN appointment_statuses st"
                + " ON st.id = a.status_id WHERE a.id = ?", String.class, appointmentId);
    }

    // ------------------------------------------------------------------ CA-01, CA-02, CA-07

    /** CA-01, CA-02 (un slot), CA-07: APPROVED futura → CANCELLED, franja otra vez ofrecida, historial USER. */
    @Test
    void cancelsAnApprovedAppointmentReleasesItsSlotAndRecordsTheUser() throws Exception {
        long id = general("08:00");
        availability(general, otherPatient).andExpect(jsonPath("$[*].startTime", not(hasItem("08:00"))));
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.reschedulable").value(true))
                .andExpect(jsonPath("$.pendingReschedule").value(false));
        int before = history(id);

        cancel(id, patient, json.writeValueAsString(Map.of("reason", "Viaje de trabajo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.reschedulable").value(false))
                .andExpect(jsonPath("$.pendingReschedule").value(false))
                .andExpect(jsonPath("$.history.length()").value(before + 1))
                .andExpect(jsonPath("$.history[" + before + "].status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[" + before + "].source").value("USER"))
                .andExpect(jsonPath("$.history[" + before + "].actorName").exists())
                .andExpect(jsonPath("$.history[" + before + "].reason").value("Viaje de trabajo"));

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(reservations(id)).isZero();
        assertThat(history(id)).isEqualTo(before + 1);
        Map<String, Object> last = jdbc.queryForMap("SELECT st.code AS status, h.actor_user_id, h.source, h.changed_at"
                + " FROM appointment_status_history h JOIN appointment_statuses st ON st.id = h.status_id"
                + " WHERE h.appointment_id = ? ORDER BY h.id DESC LIMIT 1", id);
        assertThat(last.get("status")).isEqualTo("CANCELLED");
        assertThat(((Number) last.get("actor_user_id")).longValue()).isEqualTo(patientId);
        assertThat(last.get("source")).isEqualTo("USER");
        assertThat(last.get("changed_at")).isNotNull();

        // RN-09: la franja vuelve a ofrecerse a cualquier paciente.
        availability(general, otherPatient).andExpect(jsonPath("$[*].startTime", hasItem("08:00")));
        mvc.perform(get("/api/patient/appointments").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].pendingReschedule").value(false));
    }

    /** D16 y CA-02 (dos slots): una REQUESTED de 60 min se cancela y libera sus dos slots; sale de la bandeja. */
    @Test
    void cancelsARequestedSixtyMinuteAppointmentAndReleasesBothSlots() throws Exception {
        long id = specialized("08:00");
        assertThat(reservations(id)).isEqualTo(2);
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.reschedulable").value(false));

        cancel(id, patient, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(reservations(id)).isZero();
        availability(cardiology, otherPatient).andExpect(jsonPath("$[*].startTime", hasItems("08:00", "08:30")));
        mvc.perform(get("/api/admin/inbox").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].appointment.id", not(hasItem((int) id))));
    }

    /** CA-02 (dos slots) sobre una APPROVED especializada, ya decidida por el ADMIN. */
    @Test
    void cancelsAnApprovedSixtyMinuteAppointment() throws Exception {
        long id = specialized("09:00");
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        cancel(id, patient, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(reservations(id)).isZero();
        availability(cardiology, otherPatient).andExpect(jsonPath("$[*].startTime", hasItem("09:00")));
    }

    /** Aclaracion 2 del contrato S4: sin cuerpo, {} y {"reason": ""} equivalen a no dar motivo. */
    @Test
    void anEmptyReasonMeansNoReason() throws Exception {
        long first = general("08:00");
        long second = general("08:30");
        cancel(first, patient, "{}").andExpect(status().isOk());
        cancel(second, patient, "{\"reason\": \"\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.history[1].reason").doesNotExist());
        assertThat(data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id IN (?, ?)"
                + " AND reason IS NOT NULL", first, second)).isZero();
    }

    /** Motivo de mas de 500 caracteres → 400 y nada cambia. */
    @Test
    void aTooLongReasonIsRejectedWithoutChanges() throws Exception {
        long id = general("08:00");
        int before = history(id);
        cancel(id, patient, json.writeValueAsString(Map.of("reason", "x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(reservations(id)).isEqualTo(1);
        assertThat(history(id)).isEqualTo(before);
    }

    // ------------------------------------------------------------------ CA-03, CA-05

    /** CA-03: una cancelada no vuelve atras por ninguna via; tampoco se cancela dos veces. Sin historial nuevo. */
    @Test
    void aCancelledAppointmentCannotBeReactivated() throws Exception {
        long id = specialized("08:00");
        cancel(id, patient, null).andExpect(status().isOk());
        int after = history(id);

        cancel(id, patient, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(history(id)).isEqualTo(after);
    }

    /** CA-05: REJECTED, COMPLETED y NO_SHOW (futuras) → 409 INVALID_TRANSITION, sin cambios ni historial. */
    @Test
    void terminalAppointmentsCannotBeCancelled() throws Exception {
        for (String terminal : new String[] { "REJECTED", "COMPLETED", "NO_SHOW" }) {
            long id = data.appointment(patientId, gp.id(), hic, general, day, LocalTime.of(9, 0),
                    LocalTime.of(9, 30), terminal);
            cancel(id, patient, null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
            assertThat(statusOf(id)).isEqualTo(terminal);
            assertThat(history(id)).isZero();
            mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                    .andExpect(jsonPath("$.cancellable").value(false));
        }
    }

    // ------------------------------------------------------------------ CA-04

    /** CA-04: una cita que ya paso → 409 APPOINTMENT_EXPIRED; conserva estado y reservas. */
    @Test
    void aPastAppointmentCannotBeCancelled() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long block = data.block(gp.id(), hic, yesterday, "08:00", "09:00");
        long id = data.appointment(patientId, gp.id(), hic, general, yesterday, LocalTime.of(8, 0),
                LocalTime.of(8, 30), "APPROVED");
        data.reserve(data.slotId(block, "08:00"), id, 1);

        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.reschedulable").value(false));
        cancel(id, patient, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_EXPIRED"));

        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(reservations(id)).isEqualTo(1);
        assertThat(history(id)).isZero();
    }

    // ------------------------------------------------------------------ CA-06 y HU-005

    /** CA-06: la cita de otro paciente responde 404 y queda intacta. */
    @Test
    void anotherPatientsAppointmentIsNotFoundAndStaysIntact() throws Exception {
        long id = general("08:00");
        int before = history(id);

        cancel(id, otherPatient, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        cancel(Long.MAX_VALUE, patient, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(reservations(id)).isEqualTo(1);
        assertThat(history(id)).isEqualTo(before);
    }

    /** HU-005 CA-04: solo el rol USER cancela; PROFESSIONAL y ADMIN → 403. Sin token → 401. */
    @Test
    void onlyUsersCanCancel() throws Exception {
        long id = general("08:00");
        cancel(id, tokens.bearer(gp.userId(), Role.PROFESSIONAL), null).andExpect(status().isForbidden());
        cancel(id, admin, null).andExpect(status().isForbidden());
        mvc.perform(post("/api/patient/appointments/" + id + "/cancel")).andExpect(status().isUnauthorized());
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    // ------------------------------------------------------------------ CA-08

    /** CA-08: si la liberacion de slots falla, no queda nada a medias: ni estado, ni historial, ni reservas. */
    @Test
    void aFailureReleasingSlotsRollsBackTheWholeCancellation() throws Exception {
        long id = specialized("08:00");
        int before = history(id);
        doThrow(new IllegalStateException("fallo simulado de persistencia"))
                .when(appointmentRepository).releaseReservations(any());

        cancel(id, patient, null).andExpect(status().isInternalServerError());

        assertThat(statusOf(id)).isEqualTo("REQUESTED");
        assertThat(reservations(id)).isEqualTo(2);
        assertThat(history(id)).isEqualTo(before);
    }

    // ------------------------------------------------------------------ CA-09 (D18)

    /**
     * CA-09 / D18: con una reprogramacion PENDING, la solicitud pasa a CANCELLED y se liberan las dos
     * franjas (la de la cita y la retenida), en la misma transaccion. La solicitud se siembra por SQL
     * porque su productor (HU-027) llega en F5; el esquema y la retencion son los reales (V3).
     */
    @Test
    void cancellingWithAPendingRescheduleCancelsTheRequestAndReleasesBothSlots() throws Exception {
        long id = general("08:00");
        long request = pendingReschedule(id, "09:00");
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE reschedule_request_id = ?", request))
                .isEqualTo(1);
        availability(general, otherPatient).andExpect(jsonPath("$[*].startTime", not(hasItem("09:00"))));
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$.pendingReschedule").value(true))
                .andExpect(jsonPath("$.reschedulable").value(false))
                .andExpect(jsonPath("$.cancellable").value(true));
        mvc.perform(get("/api/patient/appointments").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(jsonPath("$[0].pendingReschedule").value(true));

        cancel(id, patient, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.pendingReschedule").value(false));

        Map<String, Object> req = jdbc.queryForMap("SELECT rs.code, r.decided_at, r.decided_by_user_id"
                + " FROM reschedule_requests r JOIN reschedule_statuses rs ON rs.id = r.status_id WHERE r.id = ?",
                request);
        assertThat(req.get("code")).isEqualTo("CANCELLED");
        assertThat(req.get("decided_at")).isNotNull();
        assertThat(((Number) req.get("decided_by_user_id")).longValue()).isEqualTo(patientId);
        assertThat(reservations(id)).isZero();
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE reschedule_request_id = ?", request))
                .isZero();
        availability(general, otherPatient).andExpect(jsonPath("$[*].startTime", hasItems("08:00", "09:00")));
    }

    /** CA-08 con D18: si falla la liberacion, la solicitud sigue PENDING y la retencion sigue en pie. */
    @Test
    void aFailureAlsoKeepsThePendingRescheduleUntouched() throws Exception {
        long id = general("08:00");
        long request = pendingReschedule(id, "09:00");
        doThrow(new IllegalStateException("fallo simulado de persistencia"))
                .when(appointmentRepository).releaseReservations(any());

        cancel(id, patient, null).andExpect(status().isInternalServerError());

        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT rs.code FROM reschedule_requests r JOIN reschedule_statuses rs"
                + " ON rs.id = r.status_id WHERE r.id = ?", String.class, request)).isEqualTo("PENDING");
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE reschedule_request_id = ?", request))
                .isEqualTo(1);
    }

    /**
     * Solicitud PENDING con su retencion de 30 min en el bloque del medico general. Desde F5 la crea el
     * productor real (HU-027, {@code POST /reschedule}); antes se sembraba por SQL.
     */
    private long pendingReschedule(long appointmentId, String start) throws Exception {
        String body = mvc.perform(post("/api/patient/appointments/" + appointmentId + "/reschedule")
                .header(HttpHeaders.AUTHORIZATION, patient).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("siteId", hic, "date", day.toString(), "startTime", start))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long request = json.readTree(body).get("id").asLong();
        assertThat(data.slotId(gpBlock, start)).isPositive();
        return request;
    }
}
