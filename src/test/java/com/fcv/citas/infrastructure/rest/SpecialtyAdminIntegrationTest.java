package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.TestTokens;

/** HU-011: CRUD de especialidades con duracion 30/60, sin borrado de lo referenciado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecialtyAdminIntegrationTest {

    private static final String PREFIX = "IT_SPEC_";
    private static final long ADMIN_ID = 900_101L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtEncoder jwtEncoder;

    private String admin;

    @BeforeEach
    void setUp() {
        admin = new TestTokens(jwtEncoder).bearer(ADMIN_ID, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM specialties WHERE code LIKE ?", PREFIX + "%");
    }

    private static String code() {
        return PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ResultActions create(String code, String type, int duration) throws Exception {
        return mvc.perform(post("/api/admin/specialties").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", code, "name", "Cardiología " + code,
                        "appointmentType", type, "durationMinutes", duration))));
    }

    private int createdId(String code, String type, int duration) throws Exception {
        String body = create(code, type, duration).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(body).get("id").asInt();
    }

    // CA-01 y CA-03
    @Test
    void createsActiveSpecialtyWithApprovalPolicyFromType() throws Exception {
        String code = code();
        create(code, "SPECIALIZED", 60)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.requiresAdminApproval").value(true))
                .andExpect(jsonPath("$.protected").value(false));
        mvc.perform(get("/api/admin/specialties").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].code", hasItem(code)));
    }

    // CA-02
    @ParameterizedTest
    @ValueSource(ints = { 0, 45, 90 })
    void rejectsDurationOtherThan30Or60(int duration) throws Exception {
        String code = code();
        create(code, "GENERAL", duration)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").value("Solo se admiten 30 o 60 minutos"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM specialties WHERE code = ?", Integer.class, code))
                .isZero();
    }

    @Test
    void rejectsDuplicateCodeWith409() throws Exception {
        String code = code();
        createdId(code, "GENERAL", 30);
        create(code, "GENERAL", 30)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.field").value("code"));
    }

    // CA-05, CA-06, CA-07: desactivar la saca de la oferta; reactivar la devuelve
    @Test
    void deactivatedSpecialtyLeavesTheCatalogAndComesBackWhenReactivated() throws Exception {
        String code = code();
        int id = createdId(code, "SPECIALIZED", 30);
        mvc.perform(patch("/api/admin/specialties/" + id + "/status").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/catalogs/specialties").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].code", not(hasItem(code))));
        mvc.perform(patch("/api/admin/specialties/" + id + "/status").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/catalogs/specialties").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].code", hasItem(code)));
    }

    @Test
    void updatesNameTypeAndDuration() throws Exception {
        int id = createdId(code(), "GENERAL", 30);
        mvc.perform(put("/api/admin/specialties/" + id).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Dermatología\",\"appointmentType\":\"SPECIALIZED\",\"durationMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dermatología"))
                .andExpect(jsonPath("$.appointmentType").value("SPECIALIZED"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
    }

    // D7: Medicina General protegida
    @Test
    void generalMedicineCannotBeDeactivatedNorDeleted() throws Exception {
        Integer id = jdbc.queryForObject("SELECT id FROM specialties WHERE code = 'MEDICINA_GENERAL'", Integer.class);
        mvc.perform(patch("/api/admin/specialties/" + id + "/status").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROTECTED_SPECIALTY"));
        mvc.perform(delete("/api/admin/specialties/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
    }

    // CA-04 (lado no referenciado): una especialidad sin uso si se borra
    @Test
    void deletesAnUnreferencedSpecialty() throws Exception {
        String code = code();
        int id = createdId(code, "GENERAL", 30);
        mvc.perform(delete("/api/admin/specialties/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM specialties WHERE code = ?", Integer.class, code))
                .isZero();
    }

    // CA-08
    @Test
    void nonAdminCannotWrite() throws Exception {
        String user = new TestTokens(jwtEncoder).bearer(900_102L, Role.USER);
        mvc.perform(post("/api/admin/specialties").header(HttpHeaders.AUTHORIZATION, user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"X\",\"name\":\"X\",\"appointmentType\":\"GENERAL\",\"durationMinutes\":30}"))
                .andExpect(status().isForbidden());
    }
}
