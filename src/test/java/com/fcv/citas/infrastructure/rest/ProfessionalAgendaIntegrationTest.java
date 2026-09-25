package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.appointment.AppointmentStatus;
import com.fcv.citas.domain.appointment.StatusChange;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-020 (agenda de citas aprobadas del profesional) y HU-021 (cierre de atencion), contrato S4
 * {@code /api/professional/appointments}. Las citas se siembran por SQL para poder situarlas en el
 * pasado, en curso o en el futuro, y en cualquier estado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfessionalAgendaIntegrationTest {

    private static final String AGENDA = "/api/professional/appointments";

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
    /** Espia sobre el adaptador real: solo se altera en la prueba de atomicidad (HU-021 CA-08). */
    @MockitoSpyBean
    private AppointmentRepository appointmentRepository;

    private S3TestData data;
    private int hic;
    private int icv;
    private int general;
    private S3TestData.Professional profA;
    private S3TestData.Professional profB;
    private long patientId;
    private String tokenA;
    private String tokenB;
    private String patient;
    private String admin;
    /** Lunes de una semana futura: la semana entera queda por delante (CA-03). */
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        TestTokens tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        icv = data.siteId("ICV");
        general = data.generalMedicineId();
        profA = data.professional("profa", new int[] { general }, hic, icv);
        profB = data.professional("profb", new int[] { general }, hic);
        patientId = data.user("patient", "USER");
        tokenA = tokens.bearer(profA.userId(), Role.PROFESSIONAL);
        tokenB = tokens.bearer(profB.userId(), Role.PROFESSIONAL);
        patient = tokens.bearer(patientId, Role.USER);
        admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        monday = LocalDate.now(SystemZone.ZONE).plusWeeks(2).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    // ------------------------------------------------------------------ ayudas

    private long seed(S3TestData.Professional prof, int site, LocalDate date, String start, String status) {
        LocalTime from = LocalTime.parse(start);
        return data.appointment(patientId, prof.id(), site, general, date, from, from.plusMinutes(30), status);
    }

    private ResultActions agenda(String token, String query) throws Exception {
        return mvc.perform(get(AGENDA + "?" + query).header(HttpHeaders.AUTHORIZATION, token));
    }

    private JsonNode agendaBody(String token, String query) throws Exception {
        return json.readTree(agenda(token, query).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private ResultActions close(long id, String action, String token) throws Exception {
        return mvc.perform(post(AGENDA + "/" + id + "/" + action).header(HttpHeaders.AUTHORIZATION, token));
    }

    private String statusOf(long appointmentId) {
        return jdbc.queryForObject("SELECT st.code FROM appointments a JOIN appointment_statuses st"
                + " ON st.id = a.status_id WHERE a.id = ?", String.class, appointmentId);
    }

    private int history(long appointmentId) {
        return data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id = ?", appointmentId);
    }

    /** Cita APPROVED de ayer: ya empezo y ya termino (cerrable, D19). */
    private long pastApproved(S3TestData.Professional prof) {
        return seed(prof, hic, LocalDate.now(SystemZone.ZONE).minusDays(1), "08:00", "APPROVED");
    }

    /** Cita APPROVED que empezo hace un minuto y cuya franja no ha terminado (HU-021 CA-04). */
    private long inProgressApproved() {
        LocalDateTime now = LocalDateTime.now(SystemZone.ZONE);
        LocalDateTime start = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(1);
        if (!start.toLocalDate().equals(now.toLocalDate())) {
            start = now.toLocalDate().atStartOfDay();
        }
        if (start.toLocalTime().isAfter(LocalTime.of(23, 29))) {
            start = now.toLocalDate().atTime(23, 29);
        }
        return data.appointment(patientId, profA.id(), hic, general, start.toLocalDate(), start.toLocalTime(),
                start.toLocalTime().plusMinutes(30), "APPROVED");
    }

    // ================================================================== HU-020

    /** HU-020 CA-01: de APPROVED, REQUESTED, REJECTED, CANCELLED, COMPLETED y NO_SHOW solo sale la APPROVED. */
    @Test
    void theAgendaListsOnlyApprovedAppointments() throws Exception {
        long approved = seed(profA, hic, monday, "08:00", "APPROVED");
        for (String other : new String[] { "REQUESTED", "REJECTED", "CANCELLED", "COMPLETED", "NO_SHOW" }) {
            seed(profA, hic, monday, "09:00", other);
        }

        JsonNode body = agendaBody(tokenA, "from=" + monday + "&to=" + monday);

        assertThat(ids(body)).containsExactly(approved);
        assertThat(body.get(0).get("status").asText()).isEqualTo("APPROVED");
        assertThat(body.get(0).get("statusName").asText()).isNotBlank();
    }

    /** HU-020 CA-02: filtro por dia (from = to), ordenado por hora de inicio. */
    @Test
    void theDayFilterReturnsOnlyThatDateOrderedByStartTime() throws Exception {
        seed(profA, hic, monday, "08:00", "APPROVED");
        LocalDate wednesday = monday.plusDays(2);
        long late = seed(profA, hic, wednesday, "11:00", "APPROVED");
        long early = seed(profA, hic, wednesday, "08:30", "APPROVED");
        seed(profA, hic, monday.plusDays(4), "08:00", "APPROVED");

        JsonNode body = agendaBody(tokenA, "from=" + wednesday + "&to=" + wednesday);

        assertThat(ids(body)).containsExactly(early, late);
        assertThat(body.get(0).get("date").asText()).isEqualTo(wednesday.toString());
        assertThat(body.get(0).get("startTime").asText()).isEqualTo("08:30");
        assertThat(body.get(0).get("endTime").asText()).isEqualTo("09:00");
        assertThat(body.get(0).get("durationMinutes").asInt()).isEqualTo(30);
        assertThat(body.get(0).get("site").get("code").asText()).isEqualTo("HIC");
        assertThat(body.get(0).get("specialty").get("code").asText()).isEqualTo("MEDICINA_GENERAL");
        assertThat(body.get(0).get("closable").asBoolean()).isFalse();
    }

    /** HU-020 CA-03: filtro por semana (lunes a domingo); la semana siguiente no aparece. */
    @Test
    void theWeekFilterExcludesTheFollowingWeek() throws Exception {
        long mon = seed(profA, hic, monday, "08:00", "APPROVED");
        long wed = seed(profA, icv, monday.plusDays(2), "08:00", "APPROVED");
        long sun = seed(profA, hic, monday.plusDays(6), "10:00", "APPROVED");
        long nextWeek = seed(profA, hic, monday.plusDays(7), "08:00", "APPROVED");

        JsonNode body = agendaBody(tokenA, "from=" + monday + "&to=" + monday.plusDays(6));

        assertThat(ids(body)).containsExactly(mon, wed, sun).doesNotContain(nextWeek);
    }

    /** HU-020 CA-04: filtro por sede, combinable con el de fecha. */
    @Test
    void theSiteFilterKeepsOnlyThatSite() throws Exception {
        long atHic = seed(profA, hic, monday, "08:00", "APPROVED");
        long atIcv = seed(profA, icv, monday, "10:00", "APPROVED");

        JsonNode body = agendaBody(tokenA, "from=" + monday + "&to=" + monday + "&siteId=" + hic);
        assertThat(ids(body)).containsExactly(atHic).doesNotContain(atIcv);

        assertThat(ids(agendaBody(tokenA, "from=" + monday + "&to=" + monday + "&siteId=" + icv)))
                .containsExactly(atIcv);
    }

    /** HU-020 CA-05: A no ve citas de B aunque pida el id de B; el titular sale del token. */
    @Test
    void aProfessionalNeverSeesAnotherProfessionalsAppointments() throws Exception {
        long ofA = seed(profA, hic, monday, "08:00", "APPROVED");
        long ofB = seed(profB, hic, monday, "08:00", "APPROVED");

        String range = "from=" + monday + "&to=" + monday;
        assertThat(ids(agendaBody(tokenA, range))).containsExactly(ofA);
        assertThat(ids(agendaBody(tokenA, range + "&professionalId=" + profB.id()))).containsExactly(ofA)
                .doesNotContain(ofB);
        assertThat(ids(agendaBody(tokenB, range))).containsExactly(ofB);
    }

    /**
     * HU-020 CA-06 y D35: del paciente solo nombre completo, tipo y numero de documento. Ni email, ni
     * telefono, ni credenciales, ni afiliacion; y solo pacientes con cita aprobada con este profesional.
     */
    @Test
    void theAgendaExposesOnlyTheMinimumPatientData() throws Exception {
        seed(profA, hic, monday, "08:00", "APPROVED");
        long outsider = data.user("outsider", "USER");
        data.appointment(outsider, profA.id(), hic, general, monday, LocalTime.of(9, 0), LocalTime.of(9, 30),
                "REQUESTED");
        Map<String, Object> user = jdbc.queryForMap("SELECT email, phone, document_number FROM users WHERE id = ?",
                patientId);
        String outsiderDocument = jdbc.queryForObject("SELECT document_number FROM users WHERE id = ?",
                String.class, outsider);

        String raw = agenda(tokenA, "from=" + monday + "&to=" + monday).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        JsonNode item = json.readTree(raw).get(0);

        List<String> fields = new ArrayList<>();
        item.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "status", "statusName", "date", "startTime", "endTime",
                "durationMinutes", "site", "specialty", "patient", "closable");
        List<String> patientFields = new ArrayList<>();
        item.get("patient").fieldNames().forEachRemaining(patientFields::add);
        assertThat(patientFields).containsExactlyInAnyOrder("fullName", "documentType", "documentNumber");
        assertThat(item.get("patient").get("fullName").asText()).isEqualTo("patient Prueba");
        assertThat(item.get("patient").get("documentType").asText()).isEqualTo("CC");
        assertThat(item.get("patient").get("documentNumber").asText()).isEqualTo(user.get("document_number"));
        assertThat(raw).doesNotContain((String) user.get("email"), "password", "hash", "affiliation", "email",
                "phone", outsiderDocument);
    }

    /** HU-020 CA-07: periodo sin citas → 200 y lista vacia. */
    @Test
    void anEmptyPeriodAnswersAnEmptyList() throws Exception {
        seed(profA, hic, monday, "08:00", "APPROVED");
        LocalDate empty = monday.plusDays(3);
        agenda(tokenA, "from=" + empty + "&to=" + empty).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** El rango es obligatorio, ordenado y de 62 dias como maximo, igual que el calendario de bloques. */
    @Test
    void theRangeIsValidated() throws Exception {
        agenda(tokenA, "to=" + monday).andExpect(status().isBadRequest());
        agenda(tokenA, "from=" + monday).andExpect(status().isBadRequest());
        agenda(tokenA, "from=" + monday + "&to=" + monday.minusDays(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        agenda(tokenA, "from=" + monday + "&to=" + monday.plusDays(63)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        agenda(tokenA, "from=" + monday + "&to=" + monday.plusDays(62)).andExpect(status().isOk());
    }

    /** DoD de HU-020 y HU-005: solo PROFESSIONAL; USER y ADMIN → 403, sin token → 401. */
    @Test
    void onlyProfessionalsReadTheAgenda() throws Exception {
        String range = "from=" + monday + "&to=" + monday;
        agenda(patient, range).andExpect(status().isForbidden());
        agenda(admin, range).andExpect(status().isForbidden());
        mvc.perform(get(AGENDA + "?" + range)).andExpect(status().isUnauthorized());
    }

    // ================================================================== HU-021

    /** HU-021 CA-01 y CA-06: COMPLETED, respuesta con el estado nuevo, una fila de historial PROFESSIONAL. */
    @Test
    void completesAnOwnStartedAppointmentAndRecordsTheProfessional() throws Exception {
        long id = pastApproved(profA);
        agendaBody(tokenA, "from=" + LocalDate.now(SystemZone.ZONE).minusDays(1) + "&to="
                + LocalDate.now(SystemZone.ZONE).minusDays(1)).forEach(n -> {
                    if (n.get("id").asLong() == id) {
                        assertThat(n.get("closable").asBoolean()).isTrue();
                    }
                });
        int before = history(id);

        close(id, "complete", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.closable").value(false))
                .andExpect(jsonPath("$.patient.fullName").value("patient Prueba"))
                .andExpect(jsonPath("$.patient.email").doesNotExist());

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(history(id)).isEqualTo(before + 1);
        Map<String, Object> last = jdbc.queryForMap("SELECT st.code AS status, h.actor_user_id, h.source, h.changed_at"
                + " FROM appointment_status_history h JOIN appointment_statuses st ON st.id = h.status_id"
                + " WHERE h.appointment_id = ? ORDER BY h.id DESC LIMIT 1", id);
        assertThat(last.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) last.get("actor_user_id")).longValue()).isEqualTo(profA.userId());
        assertThat(last.get("source")).isEqualTo("PROFESSIONAL");
        assertThat(last.get("changed_at")).isNotNull();
        // Una consulta posterior la ve COMPLETED (el ADMIN ve el detalle con su historial).
        mvc.perform(get("/api/admin/appointments/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.history[" + before + "].source").value("PROFESSIONAL"));
    }

    /** HU-021 CA-02 y CA-06: NO_SHOW con su historial. */
    @Test
    void marksAnOwnStartedAppointmentAsNoShow() throws Exception {
        long id = pastApproved(profA);
        int before = history(id);

        close(id, "no-show", tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.closable").value(false));

        assertThat(statusOf(id)).isEqualTo("NO_SHOW");
        assertThat(history(id)).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT source FROM appointment_status_history WHERE appointment_id = ?"
                + " ORDER BY id DESC LIMIT 1", String.class, id)).isEqualTo("PROFESSIONAL");
    }

    /** Aclaracion 6 del contrato S4: una cita cerrada sale de la agenda (que solo lista APPROVED). */
    @Test
    void aClosedAppointmentLeavesTheAgenda() throws Exception {
        long id = pastApproved(profA);
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        assertThat(ids(agendaBody(tokenA, "from=" + yesterday + "&to=" + yesterday))).contains(id);
        close(id, "complete", tokenA).andExpect(status().isOk());
        assertThat(ids(agendaBody(tokenA, "from=" + yesterday + "&to=" + yesterday))).doesNotContain(id);
    }

    /** Contrato S4: COMPLETED y NO_SHOW no liberan las reservas de la cita. */
    @Test
    void closingDoesNotReleaseTheReservations() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        long block = data.block(profA.id(), hic, yesterday, "08:00", "09:00");
        long id = pastApproved(profA);
        data.reserve(data.slotId(block, "08:00"), id, 1);

        close(id, "no-show", tokenA).andExpect(status().isOk());

        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id = ?", id)).isOne();
    }

    /** HU-021 CA-03: A no cierra la cita de B (404, como si no existiera); ni estado ni historial cambian. */
    @Test
    void aProfessionalCannotCloseAnotherProfessionalsAppointment() throws Exception {
        long ofB = pastApproved(profB);
        int before = history(ofB);

        close(ofB, "complete", tokenA).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        close(ofB, "no-show", tokenA).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        close(Long.MAX_VALUE, "complete", tokenA).andExpect(status().isNotFound());

        assertThat(statusOf(ofB)).isEqualTo("APPROVED");
        assertThat(history(ofB)).isEqualTo(before);
    }

    /**
     * HU-021 CA-04 (D19): una cita futura → 409 APPOINTMENT_NOT_STARTED sin cambios; una que ya
     * empezo pero cuya franja no ha terminado si se cierra.
     */
    @Test
    void closingDependsOnTheStartTimeNotTheEnd() throws Exception {
        long future = seed(profA, hic, monday, "08:00", "APPROVED");
        long inProgress = inProgressApproved();

        close(future, "complete", tokenA).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTED"));
        close(future, "no-show", tokenA).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTED"));
        assertThat(statusOf(future)).isEqualTo("APPROVED");
        assertThat(history(future)).isZero();

        close(inProgress, "no-show", tokenA).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
        assertThat(statusOf(inProgress)).isEqualTo("NO_SHOW");
    }

    /** HU-021 CA-05: COMPLETED y CANCELLED (y el resto de estados no APPROVED) → 409 INVALID_TRANSITION. */
    @Test
    void nonApprovedAppointmentsCannotBeClosed() throws Exception {
        LocalDate yesterday = LocalDate.now(SystemZone.ZONE).minusDays(1);
        for (String state : new String[] { "COMPLETED", "CANCELLED", "NO_SHOW", "REJECTED", "REQUESTED" }) {
            long id = seed(profA, hic, yesterday, "10:00", state);
            close(id, "complete", tokenA).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
            close(id, "no-show", tokenA).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
            assertThat(statusOf(id)).isEqualTo(state);
            assertThat(history(id)).isZero();
        }
    }

    /** HU-021 CA-07 (RN-11): las transiciones de cierre son consultables en el dominio y deterministas. */
    @Test
    void theClosingTransitionsAreDeclaredInTheDomain() {
        for (AppointmentStatus target : new AppointmentStatus[] { AppointmentStatus.COMPLETED,
                AppointmentStatus.NO_SHOW }) {
            assertThat(Arrays.stream(AppointmentStatus.values()).filter(s -> s.canTransitionTo(target)))
                    .containsExactly(AppointmentStatus.APPROVED);
        }
    }

    /**
     * HU-021 CA-08: la escritura del historial falla DESPUES de actualizar el estado (actor
     * inexistente → {@code fk_ash_actor}). Nada queda: la cita sigue APPROVED y sin historial huerfano.
     */
    @Test
    void aFailureWritingTheHistoryRollsBackTheStatusChange() throws Exception {
        long id = pastApproved(profA);
        int before = history(id);
        // La primera llamada se desvia a una segunda con el actor roto; esa segunda (reentrada) ejecuta
        // el adaptador real: actualiza el estado y la insercion del historial falla por la FK.
        AtomicBoolean reentered = new AtomicBoolean();
        doAnswer(invocation -> {
            if (reentered.get()) {
                return invocation.callRealMethod();
            }
            Appointment.Transition real = invocation.getArgument(0);
            StatusChange broken = new StatusChange(real.change().status(), Long.MAX_VALUE, real.change().source(),
                    real.change().reason());
            reentered.set(true);
            try {
                appointmentRepository.apply(new Appointment.Transition(real.appointment(), broken));
            } finally {
                reentered.set(false);
            }
            return null;
        }).when(appointmentRepository).apply(any());

        int status = close(id, "complete", tokenA).andReturn().getResponse().getStatus();

        assertThat(status).isGreaterThanOrEqualTo(400);
        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(history(id)).isEqualTo(before);
    }

    /** HU-005: solo PROFESSIONAL cierra; USER y ADMIN → 403, sin token → 401. Nada cambia. */
    @Test
    void onlyProfessionalsClose() throws Exception {
        long id = pastApproved(profA);
        close(id, "complete", patient).andExpect(status().isForbidden());
        close(id, "no-show", admin).andExpect(status().isForbidden());
        mvc.perform(post(AGENDA + "/" + id + "/complete")).andExpect(status().isUnauthorized());
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }
}
