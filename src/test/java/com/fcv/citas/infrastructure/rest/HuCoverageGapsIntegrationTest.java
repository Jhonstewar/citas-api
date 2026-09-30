package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/** Huecos de cobertura senalados por el verificador: HU-011 CA-04/CA-05, HU-016 CA-03/CA-04, HU-029 CA-06. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HuCoverageGapsIntegrationTest {

    private static final String INACTIVE = "{\"active\":false}";

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
    private long patientId;
    private String patientToken;
    private String admin;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
        hic = data.siteId("HIC");
        patientId = data.user("patient", "USER");
        patientToken = tokens.bearer(patientId, Role.USER);
        admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        day = LocalDate.now(SystemZone.ZONE).plusDays(6);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private int specialtyRows(int id) {
        return data.count("SELECT COUNT(*) FROM specialties WHERE id = ?", id);
    }

    /** HU-011 CA-04: referenciada solo por asignacion a profesional: 409 y la fila sigue. */
    @Test
    void deletingSpecialtyAssignedToProfessionalIsRejected() throws Exception {
        int sp = data.specialty("SPECIALIZED", 30);
        data.professional("assigned", new int[] { sp }, hic);
        mvc.perform(delete("/api/admin/specialties/" + sp).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIALTY_REFERENCED"));
        assertThat(specialtyRows(sp)).isEqualTo(1);
    }

    /** HU-011 CA-04: referenciada solo por una cita (sin asignacion vigente): 409 y la fila sigue. */
    @Test
    void deletingSpecialtyReferencedByAppointmentIsRejected() throws Exception {
        int other = data.specialty("SPECIALIZED", 30);
        int sp = data.specialty("SPECIALIZED", 30);
        S3TestData.Professional pro = data.professional("byappt", new int[] { other, sp }, hic);
        data.appointment(patientId, pro.id(), hic, sp, day, LocalTime.of(8, 0), LocalTime.of(8, 30), "REQUESTED");
        jdbc.update("DELETE FROM professional_specialties WHERE professional_id = ? AND specialty_id = ?",
                pro.id(), sp);
        assertThat(data.count("SELECT COUNT(*) FROM professional_specialties WHERE specialty_id = ?", sp)).isZero();
        mvc.perform(delete("/api/admin/specialties/" + sp).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIALTY_REFERENCED"));
        assertThat(specialtyRows(sp)).isEqualTo(1);
    }

    /** HU-011 CA-05: desactivar una especialidad no altera las citas existentes (paciente y admin). */
    @Test
    void deactivatingSpecialtyKeepsItsAppointmentsReadable() throws Exception {
        int sp = data.specialty("SPECIALIZED", 30);
        S3TestData.Professional pro = data.professional("deact", new int[] { sp }, hic);
        long appt = data.appointment(patientId, pro.id(), hic, sp, day, LocalTime.of(9, 0), LocalTime.of(9, 30),
                "APPROVED");
        mvc.perform(patch("/api/admin/specialties/" + sp + "/status").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(INACTIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/patient/appointments/" + appt).header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.specialty.id").value(sp))
                .andExpect(jsonPath("$.date").value(day.toString()))
                .andExpect(jsonPath("$.startTime").value("09:00"));
        mvc.perform(get("/api/admin/appointments/" + appt).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.specialty.id").value(sp));
        assertThat(data.count("SELECT COUNT(*) FROM appointments WHERE id = ? AND specialty_id = ?", appt, sp))
                .isEqualTo(1);
    }

    /** HU-016 CA-03/CA-04: desactivado por el endpoint ADMIN, reservar da 422 sin persistir nada. */
    @Test
    void professionalDeactivatedByAdminCannotBeBooked() throws Exception {
        int general = data.generalMedicineId();
        S3TestData.Professional pro = data.professional("inactive", new int[] { general }, hic);
        long block = data.block(pro.id(), hic, day, "08:00", "10:00");
        mvc.perform(patch("/api/admin/professionals/" + pro.id() + "/status")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(INACTIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("professionalId", pro.id());
        body.put("siteId", hic);
        body.put("specialtyId", general);
        body.put("date", day.toString());
        body.put("startTime", "08:00");
        mvc.perform(post("/api/patient/appointments/general").header(HttpHeaders.AUTHORIZATION, patientToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_INACTIVE"));
        assertThat(data.count("SELECT COUNT(*) FROM appointments WHERE professional_id = ?", pro.id())).isZero();
        assertThat(data.count("SELECT COUNT(*) FROM slot_reservations r JOIN availability_slots s"
                + " ON s.id = r.slot_id WHERE s.availability_block_id = ?", block)).isZero();
    }

    /** HU-029 CA-06: la bandeja es solo de ADMIN. */
    @Test
    void inboxIsAdminOnly() throws Exception {
        S3TestData.Professional pro = data.professional("inboxpro", new int[] { data.generalMedicineId() }, hic);
        mvc.perform(get("/api/admin/inbox").header(HttpHeaders.AUTHORIZATION, patientToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/inbox").header(HttpHeaders.AUTHORIZATION,
                tokens.bearer(pro.userId(), Role.PROFESSIONAL))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/inbox")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/inbox").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
    }
}
