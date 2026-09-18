package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/** HU-029 (bandeja), HU-030 (aprobar/rechazar) y HU-032 (historial de la decision). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDecisionIntegrationTest {

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
    private int cardiology;
    private int general;
    private S3TestData.Professional cardiologist;
    private S3TestData.Professional gp;
    private long adminId;
    private String admin;
    private String patientToken;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        general = data.generalMedicineId();
        cardiology = data.specialty("SPECIALIZED", 60);
        cardiologist = data.professional("cardio", new int[] { cardiology }, hic);
        gp = data.professional("gp", new int[] { general }, hic);
        adminId = data.user("admin", "ADMIN");
        admin = tokens.bearer(adminId, Role.ADMIN);
        patientToken = tokens.bearer(data.user("patient", "USER"), Role.USER);
        day = LocalDate.now(SystemZone.ZONE).plusDays(4);
        data.block(cardiologist.id(), hic, day, "08:00", "10:00");
        data.block(gp.id(), hic, day, "08:00", "10:00");
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private long request(String start) throws Exception {
        String body = mvc.perform(post("/api/patient/appointments/specialized")
                .header(HttpHeaders.AUTHORIZATION, patientToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("professionalId", cardiologist.id(), "siteId", hic,
                        "specialtyId", cardiology, "date", day.toString(), "startTime", start))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private MockHttpServletRequestBuilder reject(long id, String reason) throws Exception {
        return post("/api/admin/appointments/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", reason)));
    }

    private int reservations(long id) {
        return data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", id);
    }

    private int history(long id) {
        return data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id = ?", id);
    }

    // ------------------------------------------------------------------ HU-029

    /** CA-01 y CA-04: solo lo pendiente especializado, con datos suficientes para decidir. */
    @Test
    void inboxListsOnlyPendingSpecializedRequestsWithPatientData() throws Exception {
        long id = request("08:00");
        mvc.perform(post("/api/patient/appointments/general").header(HttpHeaders.AUTHORIZATION, patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("professionalId",
                        gp.id(), "siteId", hic, "specialtyId", general, "date", day.toString(), "startTime", "08:00"))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/inbox?professionalId=" + cardiologist.id()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("APPOINTMENT_REQUEST"))
                .andExpect(jsonPath("$[0].appointment.id").value(id))
                .andExpect(jsonPath("$[0].appointment.patient.documentNumber").exists())
                .andExpect(jsonPath("$[0].appointment.durationMinutes").value(60))
                .andExpect(jsonPath("$[0].appointment.site.code").value("HIC"));
        mvc.perform(get("/api/admin/inbox?professionalId=" + gp.id()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** CA-02 y CA-03: filtros combinables. */
    @Test
    void inboxFiltersCombine() throws Exception {
        long id = request("08:00");
        int icv = data.siteId("ICV");
        mvc.perform(get("/api/admin/inbox?siteId=" + icv + "&professionalId=" + cardiologist.id())
                .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/inbox?siteId=" + hic + "&specialtyId=" + cardiology + "&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].appointment.id", hasItem((int) id)));
        mvc.perform(get("/api/admin/inbox?specialtyId=" + cardiology + "&date=" + day.plusDays(1))
                .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------ HU-030

    /** CA-01, CA-05 y HU-029 CA-05: aprobar conserva las reservas, deja historial ADMIN y sale de la bandeja. */
    @Test
    void approvalKeepsReservationsAndLeavesTheInbox() throws Exception {
        long id = request("08:00");
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].source").value("ADMIN"));
        assertThat(reservations(id)).isEqualTo(2);
        assertThat(((Number) jdbc.queryForObject("SELECT actor_user_id FROM appointment_status_history"
                + " WHERE appointment_id = ? ORDER BY id DESC LIMIT 1", Long.class, id)).longValue()).isEqualTo(adminId);
        mvc.perform(get("/api/admin/inbox").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].appointment.id", not(hasItem((int) id))));
    }

    /** CA-02, CA-05 y CA-06: rechazo con motivo libera los slots y el paciente ve el motivo. */
    @Test
    void rejectionReleasesSlotsAndThePatientSeesTheReason() throws Exception {
        long id = request("08:00");
        mvc.perform(reject(id, "Agenda del especialista saturada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Agenda del especialista saturada"));
        assertThat(reservations(id)).isZero();
        mvc.perform(get("/api/patient/availability?specialtyId=" + cardiology + "&date=" + day)
                .header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$[*].startTime", hasItem("08:00")));
        mvc.perform(get("/api/patient/appointments/" + id).header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Agenda del especialista saturada"))
                .andExpect(jsonPath("$.history[1].reason").value("Agenda del especialista saturada"));
    }

    /** CA-03 (RN-04) y HU-032 CA-04: sin motivo → 400, nada cambia. */
    @Test
    void rejectionWithoutReasonChangesNothing() throws Exception {
        long id = request("08:00");
        mvc.perform(reject(id, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(post("/api/admin/appointments/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
        assertThat(reservations(id)).isEqualTo(2);
        assertThat(history(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT s.code FROM appointments a JOIN appointment_statuses s"
                + " ON s.id = a.status_id WHERE a.id = ?", String.class, id)).isEqualTo("REQUESTED");
    }

    /** CA-04 (RN-11): decidir una cita que ya no esta REQUESTED → 409 sin cambios. */
    @Test
    void decidingANonRequestedAppointmentIs409() throws Exception {
        long id = request("08:00");
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mvc.perform(reject(id, "tarde"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertThat(history(id)).isEqualTo(2);
        assertThat(reservations(id)).isEqualTo(2);
    }

    /** D12: una solicitud cuya franja ya paso no se aprueba. */
    @Test
    void expiredRequestCannotBeApproved() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long patient = data.user("late", "USER");
        long id = data.appointment(patient, cardiologist.id(), hic, cardiology, yesterday, LocalTime.of(8, 0),
                LocalTime.of(9, 0), "REQUESTED");
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_EXPIRED"));
    }

    /** CA-07: aprobar y rechazar a la vez → una gana, la otra 409, un solo registro de decision. */
    @Test
    void concurrentDecisionsLetExactlyOneWin() throws Exception {
        long id = request("08:00");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> decisions = List.of(
                    () -> {
                        start.await();
                        return mvc.perform(post("/api/admin/appointments/" + id + "/approve")
                                .header(HttpHeaders.AUTHORIZATION, admin)).andReturn().getResponse().getStatus();
                    },
                    () -> {
                        start.await();
                        return mvc.perform(reject(id, "simultánea")).andReturn().getResponse().getStatus();
                    });
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> d : decisions) {
                futures.add(pool.submit(d));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(history(id)).isEqualTo(2);
    }

    /** CA-08: solo ADMIN decide. */
    @Test
    void onlyAdminDecides() throws Exception {
        long id = request("08:00");
        mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isForbidden());
        assertThat(history(id)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ HU-032

    /** CA-06 (RN-12): no hay rutas para modificar o borrar citas ni historial. */
    @Test
    void historyAndAppointmentsCannotBeDeletedThroughTheApi() throws Exception {
        long id = request("08:00");
        mvc.perform(delete("/api/admin/appointments/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isMethodNotAllowed());
        assertThat(history(id)).isEqualTo(1);
    }

    @Test
    void summaryCountsPendingRequests() throws Exception {
        request("08:00");
        mvc.perform(get("/api/admin/summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingRequests").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeProfessionals").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }
}
