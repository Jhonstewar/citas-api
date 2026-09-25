package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.auth.RefreshTokenHasher;

/**
 * HU-006 CA-05 y la prueba de extremo a extremo de HU-007 con la exposicion de laboratorio
 * ACTIVA ({@code PASSWORD_RESET_EXPOSE_TOKEN=true}, D27): el token llega en la respuesta y el
 * flujo se completa sin mirar la base de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.password-reset.expose-token=true")
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryExposedTokenIntegrationTest {

    private static final String DOMAIN = "@it-recovery-dev.fcv.test";
    private static final String OLD_PASSWORD = "Clave-Vieja#2026";
    private static final String NEW_PASSWORD = "Clave-Nueva#2027";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%" + DOMAIN);
    }

    private static String uniqueEmail() {
        return "dev-" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private void register(String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstNames", "Mateo");
        body.put("lastNames", "Laboratorio");
        body.put("documentType", "CC");
        body.put("documentNumber", "DV" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        body.put("email", email);
        body.put("phone", "3001234567");
        body.put("password", OLD_PASSWORD);
        postJson("/api/auth/register", body).andExpect(status().isCreated());
    }

    private JsonNode recover(String email) throws Exception {
        return json.readTree(postJson("/api/auth/password-recovery", Map.of("email", email))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return postJson("/api/auth/password-reset", Map.of("token", token, "newPassword", newPassword));
    }

    /** CA-05: el token viaja en la respuesta, solo para un email existente, y nunca en el log. */
    @Test
    void devTokenIsExposedOnlyForExistingEmailsAndNeverLogged(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email);

        JsonNode known = recover(email);
        JsonNode unknown = recover("no.registrado" + DOMAIN);

        String devToken = known.get("devToken").asText();
        assertThat(devToken).isNotBlank();
        assertThat(unknown.has("devToken")).isFalse();
        // El mensaje sigue siendo el mismo; solo la extension de laboratorio los distingue.
        assertThat(known.get("message")).isEqualTo(unknown.get("message"));
        assertThat(jdbc.queryForObject("""
                SELECT t.token_hash FROM password_reset_tokens t JOIN users u ON u.id = t.user_id
                WHERE u.email = ?""", String.class, email)).isEqualTo(RefreshTokenHasher.sha256Hex(devToken));
        assertThat(output.getAll()).doesNotContain(devToken);
    }

    /**
     * DoD de HU-007: solicitud → restablecimiento → login con la nueva (200) y con la anterior
     * (401), y el mismo token no sirve dos veces. Todo por la API, sin consultar la base.
     */
    @Test
    void endToEndRecoveryResetAndLogin(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email);

        String token = recover(email).get("devToken").asText();
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        postJson("/api/auth/login", Map.of("email", email, "password", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        postJson("/api/auth/login", Map.of("email", email, "password", OLD_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
        reset(token, "Tercera-Clave#2028")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        assertThat(output.getAll()).doesNotContain(token).doesNotContain(NEW_PASSWORD)
                .doesNotContain("Tercera-Clave#2028");
    }

    /** Pedir un token nuevo invalida el anterior: solo el ultimo enlace sirve. */
    @Test
    void aNewRequestInvalidatesThePreviousToken() throws Exception {
        String email = uniqueEmail();
        register(email);

        String first = recover(email).get("devToken").asText();
        String second = recover(email).get("devToken").asText();
        assertThat(second).isNotEqualTo(first);

        reset(first, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }
}
