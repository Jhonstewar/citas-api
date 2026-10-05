package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.application.appointment.AppointmentEvent;
import com.fcv.citas.application.appointment.AppointmentEventPublisher;
import com.fcv.citas.application.appointment.AppointmentEventType;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-035 (CA-01 a CA-04, D-E, D-F): un evento por decision/cancelacion confirmada, ninguno en las
 * demas operaciones ni cuando la transaccion falla, y un publicador que falla no cambia el resultado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentEventPublishingIntegrationTest {

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
    @MockitoBean
    private AppointmentEventPublisher publisher;

    private S3TestData data;
    private int hic;
    private int general;
    private int cardiology;
    private S3TestData.Professional gp;
    private S3TestData.Professional cardiologist;
    private long patientId;
    private String patient;
    private String admin;
    private String professionalToken;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        TestTokens tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        general = data.generalMedicineId();
        cardiology = data.specialty("SPECIALIZED", 60);
        gp = data.professional("gp", new int[] { general }, hic);
        cardiologist = data.professional("cardio", new int[] { cardiology }, hic);
        patientId = data.user("patient", "USER");
        patient = tokens.bearer(patientId, Role.USER);
        admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        professionalToken = tokens.bearer(gp.userId(), Role.PROFESSIONAL);
        day = LocalDate.now(SystemZone.ZONE).plusDays(5);
        data.block(gp.id(), hic, day, "08:00", "12:00");
        data.block(cardiologist.id(), hic, day, "08:00", "12:00");
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

    private long requested(String start) throws Exception {
        return book("specialized", cardiologist.id(), cardiology, start);
    }

    private long approvedCardiology(String start) throws Exception {
        long id = requested(start);
        approve(id).andExpect(status().isOk());
        return id;
    }

    private ResultActions approve(long id) throws Exception {
        return mvc.perform(post("/api/admin/appointments/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, admin));
    }

    private ResultActions reject(long id, String reason) throws Exception {
        return mvc.perform(post("/api/admin/appointments/" + id + "/reject").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", reason))));
    }

    private long requestReschedule(long appointmentId, String start) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("siteId", hic);
        body.put("date", day.toString());
        body.put("startTime", start);
        String response = mvc.perform(post("/api/patient/appointments/" + appointmentId + "/reschedule")
                .header(HttpHeaders.AUTHORIZATION, patient).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions decideReschedule(long requestId, String action, String reason) throws Exception {
        var request = post("/api/admin/reschedules/" + requestId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, admin);
        if (reason != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", reason)));
        }
        return mvc.perform(request);
    }

    private AppointmentEvent onlyEvent() {
        ArgumentCaptor<AppointmentEvent> captor = ArgumentCaptor.forClass(AppointmentEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private List<AppointmentEvent> allEvents(int expected) {
        ArgumentCaptor<AppointmentEvent> captor = ArgumentCaptor.forClass(AppointmentEvent.class);
        verify(publisher, org.mockito.Mockito.times(expected)).publish(captor.capture());
        return captor.getAllValues();
    }

    private String statusOf(long appointmentId) {
        return jdbc.queryForObject("SELECT st.code FROM appointments a JOIN appointment_statuses st"
                + " ON st.id = a.status_id WHERE a.id = ?", String.class, appointmentId);
    }

    // ------------------------------------------------------------------ CA-01

    @Test
    void approvingASpecializedRequestEmitsOneApprovedEvent() throws Exception {
        long id = requested("08:00");
        approve(id).andExpect(status().isOk());
        AppointmentEvent event = onlyEvent();
        assertThat(event.type()).isEqualTo(AppointmentEventType.APPOINTMENT_APPROVED);
        assertThat(event.appointmentId()).isEqualTo(id);
        assertThat(event.appointment().status()).isEqualTo("APPROVED");
        assertThat(event.appointment().patient().email()).isNotBlank();
        assertThat(event.eventId().version()).isEqualTo(4);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.reason()).isNull();
    }

    @Test
    void rejectingASpecializedRequestEmitsOneRejectedEventWithReason() throws Exception {
        long id = requested("08:00");
        reject(id, "Sin cupo en la especialidad").andExpect(status().isOk());
        AppointmentEvent event = onlyEvent();
        assertThat(event.type()).isEqualTo(AppointmentEventType.APPOINTMENT_REJECTED);
        assertThat(event.appointmentId()).isEqualTo(id);
        assertThat(event.appointment().status()).isEqualTo("REJECTED");
        assertThat(event.reason()).isEqualTo("Sin cupo en la especialidad");
    }

    @Test
    void approvingAndRejectingAReschedulingEmitRescheduleEvents() throws Exception {
        long first = approvedCardiology("08:00");
        long second = approvedCardiology("10:00");
        long approvedRequest = requestReschedule(first, "09:00");
        decideReschedule(approvedRequest, "approve", null).andExpect(status().isOk());
        long rejectedRequest = requestReschedule(second, "11:00");
        decideReschedule(rejectedRequest, "reject", "Franja no disponible").andExpect(status().isOk());

        // 2 aprobaciones de cita + 2 decisiones de reprogramacion; la solicitud NO emite.
        List<AppointmentEvent> events = allEvents(4);
        assertThat(events).extracting(AppointmentEvent::type).containsExactly(
                AppointmentEventType.APPOINTMENT_APPROVED, AppointmentEventType.APPOINTMENT_APPROVED,
                AppointmentEventType.RESCHEDULE_APPROVED, AppointmentEventType.RESCHEDULE_REJECTED);
        assertThat(events.get(2).appointmentId()).isEqualTo(first);
        assertThat(events.get(3).appointmentId()).isEqualTo(second);
        assertThat(events.get(3).reason()).isEqualTo("Franja no disponible");
    }

    @Test
    void cancellingEmitsOneCancelledEvent() throws Exception {
        long id = approvedCardiology("08:00");
        mvc.perform(post("/api/patient/appointments/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, patient)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", "Viaje"))))
                .andExpect(status().isOk());
        List<AppointmentEvent> events = allEvents(2);
        AppointmentEvent cancelled = events.get(1);
        assertThat(cancelled.type()).isEqualTo(AppointmentEventType.APPOINTMENT_CANCELLED);
        assertThat(cancelled.appointmentId()).isEqualTo(id);
        assertThat(cancelled.appointment().status()).isEqualTo("CANCELLED");
        assertThat(cancelled.reason()).isEqualTo("Viaje");
    }

    @Test
    void everyEventHasADistinctEventId() throws Exception {
        long a = requested("08:00");
        long b = requested("10:00");
        approve(a).andExpect(status().isOk());
        reject(b, "Motivo").andExpect(status().isOk());
        List<UUID> ids = allEvents(2).stream().map(AppointmentEvent::eventId).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------ D-E: sin evento

    @Test
    void autoApprovedGeneralBookingEmitsNothing() throws Exception {
        book("general", gp.id(), general, "08:00");
        verify(publisher, never()).publish(any());
    }

    @Test
    void completingAndNoShowEmitNothing() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long toComplete = data.appointment(patientId, gp.id(), hic, general, yesterday, LocalTime.of(8, 0),
                LocalTime.of(8, 30), "APPROVED");
        long toMiss = data.appointment(patientId, gp.id(), hic, general, yesterday, LocalTime.of(9, 0),
                LocalTime.of(9, 30), "APPROVED");
        mvc.perform(post("/api/professional/appointments/" + toComplete + "/complete")
                .header(HttpHeaders.AUTHORIZATION, professionalToken)).andExpect(status().isOk());
        mvc.perform(post("/api/professional/appointments/" + toMiss + "/no-show")
                .header(HttpHeaders.AUTHORIZATION, professionalToken)).andExpect(status().isOk());
        verify(publisher, never()).publish(any());
    }

    @Test
    void requestingAReschedulingEmitsNothing() throws Exception {
        long id = approvedCardiology("08:00");
        org.mockito.Mockito.clearInvocations(publisher);
        requestReschedule(id, "09:00");
        verify(publisher, never()).publish(any());
    }

    // ------------------------------------------------------------------ CA-02

    @Test
    void invalidTransitionEmitsNothing() throws Exception {
        long id = requested("08:00");
        approve(id).andExpect(status().isOk());
        org.mockito.Mockito.clearInvocations(publisher);
        approve(id).andExpect(status().isConflict());
        reject(id, "Tarde").andExpect(status().isConflict());
        verify(publisher, never()).publish(any());
    }

    @Test
    void rejectionWithoutReasonEmitsNothing() throws Exception {
        long id = requested("08:00");
        reject(id, " ").andExpect(status().isBadRequest());
        verify(publisher, never()).publish(any());
    }

    // ------------------------------------------------------------------ CA-03

    /**
     * El publicador espia consulta la fila por una conexion propia (JdbcTemplate fuera de la
     * transaccion de la peticion): solo ve el estado nuevo si el commit ya ocurrio.
     */
    @Test
    void thePublisherObservesTheAlreadyCommittedState() throws Exception {
        long toApprove = requested("08:00");
        long toReject = requested("09:00");
        long toCancel = approvedCardiology("10:00");
        org.mockito.Mockito.clearInvocations(publisher);

        Map<AppointmentEventType, String> observed = new java.util.concurrent.ConcurrentHashMap<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            AppointmentEvent event = invocation.getArgument(0);
            observed.put(event.type(), statusOf(event.appointmentId()));
            return null;
        }).when(publisher).publish(any());

        approve(toApprove).andExpect(status().isOk());
        reject(toReject, "Sin cupo").andExpect(status().isOk());
        mvc.perform(post("/api/patient/appointments/" + toCancel + "/cancel").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(status().isOk());

        assertThat(observed).containsEntry(AppointmentEventType.APPOINTMENT_APPROVED, "APPROVED")
                .containsEntry(AppointmentEventType.APPOINTMENT_REJECTED, "REJECTED")
                .containsEntry(AppointmentEventType.APPOINTMENT_CANCELLED, "CANCELLED");
    }

    // ------------------------------------------------------------------ CA-04

    @Test
    void aFailingPublisherDoesNotChangeTheOutcome() throws Exception {
        doThrow(new IllegalStateException("destino caido: ana@ejemplo.test")).when(publisher).publish(any());
        long id = requested("08:00");
        approve(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(statusOf(id)).isEqualTo("APPROVED");

        long other = requested("10:00");
        reject(other, "Sin cupo").andExpect(status().isOk());
        assertThat(statusOf(other)).isEqualTo("REJECTED");

        mvc.perform(post("/api/patient/appointments/" + id + "/cancel").header(HttpHeaders.AUTHORIZATION, patient))
                .andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
    }
}
