package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
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
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.EpsTestData;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-008: consultar y actualizar el perfil propio ({@code GET/PUT /api/me}). Editables: nombres,
 * apellidos y telefono (D25); cualquier campo fijo en el cuerpo → 400 {@code FIELD_NOT_EDITABLE}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileIntegrationTest {

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

    private S3TestData users;
    private EpsTestData eps;
    private TestTokens tokens;
    private long userId;
    private String user;
    private long otherId;

    @BeforeEach
    void setUp() {
        users = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        eps = new EpsTestData(jdbc);
        tokens = new TestTokens(jwtEncoder);
        userId = users.user("ana", "USER");
        user = tokens.bearer(userId, Role.USER);
        otherId = users.user("otro", "USER");
    }

    @AfterEach
    void cleanUp() {
        users.cleanUp();
        eps.cleanUp();
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions update(String token, Object body) throws Exception {
        return mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT first_names, last_names, phone, email, document_number, password_hash"
                + " FROM users WHERE id = ?", id);
    }

    private static Map<String, Object> valid() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstNames", "Ana María");
        body.put("lastNames", "Pérez Gómez");
        body.put("phone", "3009998877");
        return body;
    }

    // ------------------------------------------------------------------ CA-01

    /** CA-01: el perfil propio con sus datos, sin contraseña ni hash; sin afiliacion, el campo se omite. */
    @Test
    void readsTheOwnProfileWithoutCredentials() throws Exception {
        Map<String, Object> stored = row(userId);
        String raw = me(user).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.firstNames").value("ana"))
                .andExpect(jsonPath("$.lastNames").value("Prueba"))
                .andExpect(jsonPath("$.email").value(stored.get("email")))
                .andExpect(jsonPath("$.documentType").value("CC"))
                .andExpect(jsonPath("$.documentNumber").value(stored.get("document_number")))
                .andExpect(jsonPath("$.phone").value("3001234567"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.affiliation").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("password", "hash", (String) stored.get("password_hash"));
    }

    /** GET /api/me incluye la afiliacion vigente con el mismo cuerpo de plan que el catalogo publico. */
    @Test
    void theProfileIncludesTheCurrentAffiliation() throws Exception {
        int epsId = eps.eps("Perfil", true);
        int plan = eps.plan(epsId, "SUBSIDIADO", "Perfil", true);
        LocalDate today = LocalDate.now(SystemZone.ZONE);
        long affiliation = eps.affiliation(userId, plan, today, null);

        me(user).andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliation.id").value(affiliation))
                .andExpect(jsonPath("$.affiliation.startedOn").value(today.toString()))
                .andExpect(jsonPath("$.affiliation.plan.id").value(plan))
                .andExpect(jsonPath("$.affiliation.plan.code").exists())
                .andExpect(jsonPath("$.affiliation.plan.name").exists())
                .andExpect(jsonPath("$.affiliation.plan.eps.id").value(epsId))
                .andExpect(jsonPath("$.affiliation.plan.eps.code").exists())
                .andExpect(jsonPath("$.affiliation.plan.regime.code").value("SUBSIDIADO"))
                .andExpect(jsonPath("$.affiliation.plan.regime.name").exists());
    }

    // ------------------------------------------------------------------ CA-02

    /** CA-02: actualizar los tres campos editables; la respuesta y una consulta posterior los reflejan. */
    @Test
    void updatesTheEditableFields() throws Exception {
        Map<String, Object> before = row(userId);

        update(user, valid()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.firstNames").value("Ana María"))
                .andExpect(jsonPath("$.lastNames").value("Pérez Gómez"))
                .andExpect(jsonPath("$.phone").value("3009998877"))
                .andExpect(jsonPath("$.email").value(before.get("email")))
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        me(user).andExpect(jsonPath("$.firstNames").value("Ana María"))
                .andExpect(jsonPath("$.phone").value("3009998877"));
        Map<String, Object> after = row(userId);
        assertThat(after.get("first_names")).isEqualTo("Ana María");
        assertThat(after.get("email")).isEqualTo(before.get("email"));
        assertThat(after.get("document_number")).isEqualTo(before.get("document_number"));
        assertThat(after.get("password_hash")).isEqualTo(before.get("password_hash"));
    }

    // ------------------------------------------------------------------ CA-03 y CA-07

    /**
     * CA-03 (D25) y CA-07: email, tipo y numero de documento, contraseña y roles no son editables. Si
     * llegan en el cuerpo → 400 FIELD_NOT_EDITABLE con {@code field}, y no se cambia NADA (tampoco los
     * editables que viajaban junto a el).
     */
    @Test
    void anyFixedFieldIsRejectedAndNothingChanges() throws Exception {
        Map<String, Object> before = row(userId);
        Map<String, Object> fixed = new LinkedHashMap<>();
        fixed.put("email", "paciente.demo@example.com");
        fixed.put("documentType", "TI");
        fixed.put("documentNumber", "999999");
        fixed.put("password", "OtraClave123");
        fixed.put("roles", List.of("ADMIN"));

        for (Map.Entry<String, Object> field : fixed.entrySet()) {
            Map<String, Object> body = valid();
            body.put(field.getKey(), field.getValue());
            update(user, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FIELD_NOT_EDITABLE"))
                    .andExpect(jsonPath("$.field").value(field.getKey()))
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        // Presente aunque sea nulo: el cliente intento tocar el campo.
        // JSON literal: el ObjectMapper de la app (non_null) omitiria la clave al serializar un Map.
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstNames\":\"Ana\",\"lastNames\":\"Pérez\",\"phone\":\"300\",\"email\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIELD_NOT_EDITABLE"))
                .andExpect(jsonPath("$.field").value("email"));

        assertThat(row(userId)).isEqualTo(before);
    }

    // ------------------------------------------------------------------ CA-04 y CA-05

    /**
     * CA-04 y CA-05: no hay ruta para leer ni escribir el perfil de otro (denegada), y un {@code id}
     * en el cuerpo no cambia de titular: el titular sale del token.
     */
    @Test
    void nobodyReadsOrUpdatesSomeoneElsesProfile() throws Exception {
        Map<String, Object> other = row(otherId);

        mvc.perform(get("/api/users/" + otherId).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + otherId).header(HttpHeaders.AUTHORIZATION, user)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(valid())))
                .andExpect(status().isForbidden());
        Map<String, Object> body = valid();
        body.put("id", otherId);
        body.put("userId", otherId);
        update(user, body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId));

        assertThat(row(otherId)).isEqualTo(other);
        assertThat(row(userId).get("first_names")).isEqualTo("Ana María");
        me(user).andExpect(jsonPath("$.id").value(userId));
    }

    // ------------------------------------------------------------------ CA-06

    /** CA-06: validacion en el servidor, con los mismos limites que el registro; nada se persiste. */
    @Test
    void invalidValuesAreRejectedPerField() throws Exception {
        Map<String, Object> before = row(userId);
        Map<String, Object[]> cases = new LinkedHashMap<>();
        cases.put("firstNames", new Object[] { "", "  ", "A".repeat(101), null });
        cases.put("lastNames", new Object[] { "", "P".repeat(101), null });
        cases.put("phone", new Object[] { "", "3".repeat(31), null });

        for (Map.Entry<String, Object[]> field : cases.entrySet()) {
            for (Object value : field.getValue()) {
                Map<String, Object> body = valid();
                body.put(field.getKey(), value);
                update(user, body).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.fieldErrors." + field.getKey()).exists());
            }
        }
        mvc.perform(put("/api/me").header(HttpHeaders.AUTHORIZATION, user).contentType(MediaType.APPLICATION_JSON)
                .content("no es json")).andExpect(status().isBadRequest());

        assertThat(row(userId)).isEqualTo(before);
    }

    /** El perfil es de cualquier rol autenticado (contrato: autenticado); sin token → 401. */
    @Test
    void anyAuthenticatedRoleEditsItsOwnProfileButNotAnonymous() throws Exception {
        long adminId = users.user("admin", "ADMIN");
        update(tokens.bearer(adminId, Role.ADMIN), valid()).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.affiliation").doesNotExist());
        mvc.perform(put("/api/me").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(valid())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    /** La respuesta nunca serializa el usuario completo: solo los campos de UserResponse (+ affiliation). */
    @Test
    void theResponseShapeIsTheUserResponse() throws Exception {
        JsonNode body = json.readTree(update(user, valid()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        List<String> fields = new ArrayList<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "firstNames", "lastNames", "documentType",
                "documentNumber", "email", "phone", "roles");
    }
}
