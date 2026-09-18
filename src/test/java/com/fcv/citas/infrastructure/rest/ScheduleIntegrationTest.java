package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/** HU-017 a HU-019: bloques de disponibilidad, slots y calendario del profesional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleIntegrationTest {

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
    private S3TestData.Professional pro;
    private String proToken;
    private int hic;
    private int icv;
    private int generalMedicine;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        hic = data.siteId("HIC");
        icv = data.siteId("ICV");
        generalMedicine = data.generalMedicineId();
        pro = data.professional("pro", new int[] { generalMedicine }, hic);
        proToken = new TestTokens(jwtEncoder).bearer(pro.userId(), Role.PROFESSIONAL);
        day = LocalDate.now(SystemZone.ZONE).plusDays(10);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private ResultActions createBlock(String token, int site, LocalDate date, String start, String end)
            throws Exception {
        return mvc.perform(post("/api/professional/blocks").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("siteId", site, "date", date.toString(), "startTime", start, "endTime", end))));
    }

    private long blockId(String start, String end) throws Exception {
        String body = createBlock(proToken, hic, day, start, end).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private int countBlocks() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM availability_blocks WHERE professional_id = ?",
                Integer.class, pro.id());
    }

    // ------------------------------------------------------------------ HU-017

    /** CA-02 (verificacion 4): 08:00–12:00 → 8 slots; y las horas se guardan tal cual, sin desfase de zona. */
    @Test
    void blockExpandsIntoEightSlotsStoredWithTheSameLocalTimes() throws Exception {
        createBlock(proToken, hic, day, "08:00", "12:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("08:00"))
                .andExpect(jsonPath("$.endTime").value("12:00"))
                .andExpect(jsonPath("$.site.code").value("HIC"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.slots.length()").value(8))
                .andExpect(jsonPath("$.slots[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.slots[7].startTime").value("11:30"))
                .andExpect(jsonPath("$.slots[7].endTime").value("12:00"))
                .andExpect(jsonPath("$.slots[3].available").value(true));
        // Lectura cruda con SQL: si Hibernate desplazara LocalTime por la zona, aqui saldria 03:00.
        List<String> stored = jdbc.queryForList("""
                SELECT TIME_FORMAT(s.start_time, '%H:%i') FROM availability_slots s
                JOIN availability_blocks b ON b.id = s.availability_block_id
                WHERE b.professional_id = ? ORDER BY s.start_time
                """, String.class, pro.id());
        assertThat(stored).containsExactly("08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30");
        assertThat(jdbc.queryForObject("SELECT TIME_FORMAT(start_time, '%H:%i') FROM availability_blocks"
                + " WHERE professional_id = ?", String.class, pro.id())).isEqualTo("08:00");
    }

    /** CA-01: varios bloques el mismo dia (ejemplo del PRD). */
    @Test
    void aDayAcceptsSeveralBlocks() throws Exception {
        blockId("08:00", "12:00");
        blockId("14:00", "17:00");
        mvc.perform(get("/api/professional/blocks?from=" + day + "&to=" + day)
                .header(HttpHeaders.AUTHORIZATION, proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].startTime").value("14:00"))
                .andExpect(jsonPath("$[1].slots.length()").value(6));
    }

    /** CA-03 (RN-06): bloque en el pasado → 400 PAST_TIME, nada persistido. */
    @Test
    void pastBlockIsRejected() throws Exception {
        createBlock(proToken, hic, LocalDate.now(SystemZone.ZONE).minusDays(1), "08:00", "10:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAST_TIME"));
        assertThat(countBlocks()).isZero();
    }

    /** CA-04: solape → 409 BLOCK_OVERLAP; el contiguo si se acepta. */
    @Test
    void overlappingBlockIsRejected() throws Exception {
        blockId("08:00", "12:00");
        createBlock(proToken, hic, day, "11:30", "13:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_OVERLAP"));
        createBlock(proToken, hic, day, "12:00", "13:00").andExpect(status().isCreated());
        assertThat(countBlocks()).isEqualTo(2);
    }

    /** CA-05 (RN-07): sede no asignada → 422. */
    @Test
    void blockAtUnassignedSiteIsRejected() throws Exception {
        createBlock(proToken, icv, day, "08:00", "10:00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SITE_NOT_ASSIGNED"));
        assertThat(countBlocks()).isZero();
    }

    /** Rejilla de 30 minutos validada en la API. */
    @Test
    void offGridTimesAreRejected() throws Exception {
        createBlock(proToken, hic, day, "08:15", "10:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists());
    }

    // ------------------------------------------------------------------ HU-018

    /** CA-01 y CA-04: editar recalcula los slots; eliminar lo saca del calendario. */
    @Test
    void editRecalculatesSlotsAndDeleteRemovesTheBlock() throws Exception {
        long id = blockId("08:00", "12:00");
        mvc.perform(put("/api/professional/blocks/" + id).header(HttpHeaders.AUTHORIZATION, proToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("siteId", hic, "date", day.toString(), "startTime", "08:00", "endTime", "10:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(4))
                .andExpect(jsonPath("$.slots[3].startTime").value("09:30"));
        mvc.perform(delete("/api/professional/blocks/" + id).header(HttpHeaders.AUTHORIZATION, proToken))
                .andExpect(status().isNoContent());
        assertThat(countBlocks()).isZero();
    }

    /** CA-02: con una cita comprometida no se edita ni se elimina. */
    @Test
    void blockWithAppointmentsCannotChange() throws Exception {
        long id = blockId("08:00", "10:00");
        long patient = data.user("patient", "USER");
        long appointment = data.appointment(patient, pro.id(), hic, generalMedicine, day, LocalTime.of(8, 0),
                LocalTime.of(8, 30), "APPROVED");
        data.reserve(data.slotId(id, "08:00:00"), appointment, 1);
        mvc.perform(delete("/api/professional/blocks/" + id).header(HttpHeaders.AUTHORIZATION, proToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_HAS_APPOINTMENTS"));
        mvc.perform(put("/api/professional/blocks/" + id).header(HttpHeaders.AUTHORIZATION, proToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("siteId", hic, "date", day.toString(), "startTime", "09:00", "endTime", "10:00"))))
                .andExpect(status().isConflict());
        // HU-019 CA-02: el calendario distingue el slot ocupado; el bloque deja de ser editable.
        mvc.perform(get("/api/professional/blocks?from=" + day + "&to=" + day)
                .header(HttpHeaders.AUTHORIZATION, proToken))
                .andExpect(jsonPath("$[0].editable").value(false))
                .andExpect(jsonPath("$[0].slots[0].available").value(false))
                .andExpect(jsonPath("$[0].slots[1].available").value(true));
    }

    /** CA-06: un profesional no opera bloques de otro (404, sin revelar el ajeno). */
    @Test
    void professionalCannotTouchAnotherProfessionalsBlock() throws Exception {
        long id = blockId("08:00", "10:00");
        S3TestData.Professional other = data.professional("other", new int[] { generalMedicine }, hic);
        String otherToken = new TestTokens(jwtEncoder).bearer(other.userId(), Role.PROFESSIONAL);
        mvc.perform(delete("/api/professional/blocks/" + id).header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isNotFound());
        assertThat(countBlocks()).isEqualTo(1);
        // HU-019 CA-03: tampoco lo ve en su calendario.
        mvc.perform(get("/api/professional/blocks?from=" + day + "&to=" + day)
                .header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------ HU-019

    /** CA-04 y CA-05: el rango acota; sin bloques responde lista vacia. */
    @Test
    void rangeFilterLimitsResultsAndEmptyCalendarIsOk() throws Exception {
        blockId("08:00", "09:00");
        createBlock(proToken, hic, day.plusDays(2), "08:00", "09:00").andExpect(status().isCreated());
        mvc.perform(get("/api/professional/blocks?from=" + day.plusDays(1) + "&to=" + day.plusDays(2))
                .header(HttpHeaders.AUTHORIZATION, proToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(day.plusDays(2).toString()));
        mvc.perform(get("/api/professional/blocks?from=" + day.plusDays(20) + "&to=" + day.plusDays(21))
                .header(HttpHeaders.AUTHORIZATION, proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
