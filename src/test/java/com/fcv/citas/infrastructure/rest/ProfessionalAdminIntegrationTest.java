package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/** HU-013 a HU-016: el ADMIN crea profesionales, asigna especialidades y sedes y los activa. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfessionalAdminIntegrationTest {

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
    private String admin;
    private int generalMedicine;
    private int cardiology;
    private int hic;
    private int icv;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
        admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        generalMedicine = data.generalMedicineId();
        cardiology = data.specialty("SPECIALIZED", 60);
        hic = data.siteId("HIC");
        icv = data.siteId("ICV");
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private Map<String, Object> body(String email, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstNames", "Andrés");
        body.put("lastNames", "Rincón");
        body.put("documentType", "CC");
        body.put("documentNumber", S3TestData.document());
        body.put("email", email);
        body.put("phone", "3001112233");
        body.put("password", S3TestData.PASSWORD);
        body.put("professionalCode", code);
        body.put("licenseNumber", "LIC-" + code);
        body.put("specialtyIds", List.of(generalMedicine, cardiology));
        body.put("primarySpecialtyId", generalMedicine);
        body.put("siteIds", List.of(hic));
        return body;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/admin/professionals").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private long createdId() throws Exception {
        String body = create(body(S3TestData.email("pro"), S3TestData.uniqueCode("P")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
            Object body) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private int countUsers(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
    }

    // ------------------------------------------------------------------ HU-013

    /** CA-01, CA-07: alta valida, sin contraseña en la respuesta y con hash en la base. */
    @Test
    void createsProfessionalWithUserRoleAndHashedPassword() throws Exception {
        String email = S3TestData.email("pro");
        String code = S3TestData.uniqueCode("P");
        String response = create(body(email, code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professionalCode").value(code))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fullName").value("Andrés Rincón"))
                .andExpect(jsonPath("$.specialties.length()").value(2))
                .andExpect(jsonPath("$.sites[0].code").value("HIC"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(S3TestData.PASSWORD);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("{bcrypt}");
        assertThat(jdbc.queryForObject("SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id"
                + " JOIN users u ON u.id = ur.user_id WHERE u.email = ?", String.class, email))
                .isEqualTo("PROFESSIONAL");
    }

    /** CA-02: el profesional creado inicia sesion y su token lleva el rol PROFESSIONAL. */
    @Test
    void createdProfessionalCanLogInWithItsRole() throws Exception {
        String email = S3TestData.email("pro");
        create(body(email, S3TestData.uniqueCode("P"))).andExpect(status().isCreated());
        String tokens = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", S3TestData.PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = json.readTree(tokens).get("accessToken").asText();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(jsonPath("$.roles[0]").value("PROFESSIONAL"));
        mvc.perform(get("/api/professional/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    /** CA-03 y CA-06: codigo duplicado → 409 con el campo, sin usuario huerfano. */
    @Test
    void duplicateCodeIsRejectedAtomically() throws Exception {
        String code = S3TestData.uniqueCode("P");
        create(body(S3TestData.email("first"), code)).andExpect(status().isCreated());
        String secondEmail = S3TestData.email("second");
        create(body(secondEmail, code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.field").value("professionalCode"));
        assertThat(countUsers(secondEmail)).isZero();
    }

    /** CA-04: email duplicado → 409 con el campo. */
    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = S3TestData.email("pro");
        create(body(email, S3TestData.uniqueCode("P"))).andExpect(status().isCreated());
        create(body(email, S3TestData.uniqueCode("P")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("email"));
    }

    /** CA-05: solo ADMIN crea. */
    @Test
    void onlyAdminCreates() throws Exception {
        String email = S3TestData.email("pro");
        mvc.perform(post("/api/admin/professionals")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(data.user("patient", "USER"), Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body(email, S3TestData.uniqueCode("P")))))
                .andExpect(status().isForbidden());
        assertThat(countUsers(email)).isZero();
    }

    /** CA-08: listado sin credenciales. */
    @Test
    void listsProfessionalsWithoutCredentials() throws Exception {
        long id = createdId();
        String list = mvc.perform(get("/api/admin/professionals").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("password");
    }

    // ------------------------------------------------------------------ HU-014

    /** CA-02 y CA-03: sin primaria o primaria fuera del conjunto → 400 y sin cambios. */
    @Test
    void invalidPrimaryIsRejectedWithoutChanges() throws Exception {
        long id = createdId();
        send(put("/api/admin/professionals/" + id + "/specialties"),
                Map.of("specialtyIds", List.of(cardiology), "primarySpecialtyId", generalMedicine))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.primarySpecialtyId").exists());
        mvc.perform(get("/api/admin/professionals/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.specialties.length()").value(2));
    }

    /** CA-04: especialidad inactiva → 422. */
    @Test
    void inactiveSpecialtyCannotBeAssigned() throws Exception {
        long id = createdId();
        int inactive = data.specialty("SPECIALIZED", 30);
        jdbc.update("UPDATE specialties SET active = FALSE WHERE id = ?", inactive);
        send(put("/api/admin/professionals/" + id + "/specialties"),
                Map.of("specialtyIds", List.of(inactive), "primarySpecialtyId", inactive))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPECIALTY_INACTIVE"));
    }

    /** CA-01 y CA-05: cambio de primaria; la anterior sigue asignada. */
    @Test
    void changesThePrimarySpecialty() throws Exception {
        long id = createdId();
        String response = send(put("/api/admin/professionals/" + id + "/specialties"),
                Map.of("specialtyIds", List.of(generalMedicine, cardiology), "primarySpecialtyId", cardiology))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode specialties = json.readTree(response).get("specialties");
        assertThat(specialties).hasSize(2);
        for (JsonNode s : specialties) {
            assertThat(s.get("primary").asBoolean()).isEqualTo(s.get("id").asInt() == cardiology);
        }
    }

    // ------------------------------------------------------------------ HU-015

    /** CA-01 y CA-02: se reemplaza el conjunto de sedes; vacio → 400 y conserva el anterior. */
    @Test
    void replacesSitesAndRejectsEmptySet() throws Exception {
        long id = createdId();
        send(put("/api/admin/professionals/" + id + "/sites"), Map.of("siteIds", List.of(hic, icv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sites[*].code", containsInAnyOrder("HIC", "ICV")));
        send(put("/api/admin/professionals/" + id + "/sites"), Map.of("siteIds", List.of()))
                .andExpect(status().isBadRequest());
        send(put("/api/admin/professionals/" + id + "/sites"), Map.of("siteIds", List.of(9999)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/professionals/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.sites.length()").value(2));
    }

    // ------------------------------------------------------------------ HU-016

    /** CA-01 y CA-02: desactivar y reactivar conservan especialidades y sedes. */
    @Test
    void deactivatesAndReactivatesKeepingAssignments() throws Exception {
        long id = createdId();
        send(patch("/api/admin/professionals/" + id + "/status"), Map.of("active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.specialties.length()").value(2));
        mvc.perform(get("/api/admin/professionals?active=true").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        send(patch("/api/admin/professionals/" + id + "/status"), Map.of("active", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sites.length()").value(1));
    }

    /** HU-013 fuera de alcance (INC-015): la edicion cambia contacto, no codigo ni matricula. */
    @Test
    void updatesContactButNotCodeOrLicense() throws Exception {
        long id = createdId();
        String before = jdbc.queryForObject("SELECT professional_code FROM professionals WHERE id = ?", String.class,
                id);
        send(put("/api/admin/professionals/" + id),
                Map.of("firstNames", "Paula", "lastNames", "Serrano", "phone", "3009998877",
                        "professionalCode", "HACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Paula Serrano"))
                .andExpect(jsonPath("$.professionalCode").value(before));
    }
}
