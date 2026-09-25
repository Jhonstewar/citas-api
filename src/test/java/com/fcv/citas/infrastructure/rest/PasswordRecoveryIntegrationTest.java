package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.auth.RefreshTokenHasher;

/**
 * HU-006 y HU-007 contra la API real, con la exposicion de laboratorio APAGADA (valor por
 * defecto, D27). Para conocer el token sin exponerlo, las pruebas de restablecimiento siembran el
 * registro con un token propio y su SHA-256, exactamente como lo guarda la aplicacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryIntegrationTest {

    private static final String DOMAIN = "@it-recovery.fcv.test";
    private static final String OLD_PASSWORD = "Clave-Vieja#2026";
    private static final String NEW_PASSWORD = "Clave-Nueva#2027";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String RECOVERY_MESSAGE =
            "Si el correo corresponde a una cuenta, recibirás las instrucciones para restablecer la contraseña";
    /** Forma del token opaco: 32 bytes en Base64 URL sin relleno. */
    private static final Pattern OPAQUE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        // password_reset_tokens y refresh_tokens caen por ON DELETE CASCADE.
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%" + DOMAIN);
    }

    // ------------------------------------------------------------------ helpers

    private static String uniqueEmail() {
        return "rec-" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private long register(String email) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstNames", "Lucía");
        body.put("lastNames", "Recupera");
        body.put("documentType", "CC");
        body.put("documentNumber", "RC" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        body.put("email", email);
        body.put("phone", "3001234567");
        body.put("password", OLD_PASSWORD);
        String response = postJson("/api/auth/register", body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions recover(String email) throws Exception {
        return postJson("/api/auth/password-recovery", Map.of("email", email));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("newPassword", newPassword);
        return postJson("/api/auth/password-reset", body);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Siembra un token con su hash, como lo guarda la aplicacion, y vigencia relativa a ahora. */
    private String seedToken(long userId, Duration validFor) {
        String token = randomToken();
        LocalDateTime now = LocalDateTime.now(BOGOTA);
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, issued_at, expires_at) VALUES (?, ?, ?, ?)",
                userId, RefreshTokenHasher.sha256Hex(token), Timestamp.valueOf(now.minusHours(1)),
                Timestamp.valueOf(now.plus(validFor)));
        return token;
    }

    private String passwordHash(long userId) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    private Map<String, Object> tokenRow(String token) {
        return jdbc.queryForMap("SELECT * FROM password_reset_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(token));
    }

    private int tokenCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class);
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ HU-006

    /** CA-01, CA-03, CA-04: token asociado, solo hash, vigencia de 30 min y sin consumir. */
    @Test
    void recoveryForARegisteredEmailCreatesAHashedSingleUseToken() throws Exception {
        String email = uniqueEmail();
        long userId = register(email);
        Instant before = Instant.now();

        recover(email.toUpperCase())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(RECOVERY_MESSAGE))
                .andExpect(jsonPath("$.devToken").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM password_reset_tokens WHERE user_id = ?", userId);
        assertThat((String) row.get("token_hash")).matches("[0-9a-f]{64}");
        Instant expiresAt = ((LocalDateTime) row.get("expires_at")).atZone(BOGOTA).toInstant();
        Instant issuedAt = ((LocalDateTime) row.get("issued_at")).atZone(BOGOTA).toInstant();
        assertThat(expiresAt).isAfter(issuedAt);
        // PASSWORD_RESET_MINUTES por defecto: 30.
        assertThat(Duration.between(issuedAt, expiresAt)).isEqualTo(Duration.ofMinutes(30));
        assertThat(expiresAt).isAfter(before.plus(Duration.ofMinutes(29)));
        assertThat(row.get("used_at")).isNull();
        assertThat(row.get("revoked_at")).isNull();
    }

    /** CA-03: la tabla no tiene ninguna columna que pueda guardar el token en claro (V1). */
    @Test
    void theTokenTableOnlyHasAHashColumn() {
        List<String> columns = jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'password_reset_tokens'""", String.class);
        assertThat(columns).containsExactlyInAnyOrder("id", "user_id", "token_hash", "issued_at", "expires_at",
                "used_at", "revoked_at", "revoked_reason");
    }

    /** CA-02 y CA-06: misma respuesta exista o no el email; sin token en la respuesta ni en el log. */
    @Test
    void recoveryAnswersIdenticallyForUnknownEmailsAndNeverLogsTheToken(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        long userId = register(email);
        int tokensBefore = tokenCount();
        int logStart = output.getAll().length();

        MvcResult unknown = recover("no.registrado" + DOMAIN).andExpect(status().isAccepted()).andReturn();
        assertThat(tokenCount()).isEqualTo(tokensBefore);
        MvcResult known = recover(email).andExpect(status().isAccepted()).andReturn();

        assertThat(known.getResponse().getStatus()).isEqualTo(unknown.getResponse().getStatus());
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        assertThat(known.getResponse().getContentType()).isEqualTo(unknown.getResponse().getContentType());
        assertThat(body(known).has("devToken")).isFalse();
        assertThat(tokenCount()).isEqualTo(tokensBefore + 1);

        String hash = jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens WHERE user_id = ?",
                String.class, userId);
        String logs = output.getAll().substring(logStart);
        // Ni el hash, ni el email, ni nada con la forma del token opaco llega al log.
        assertThat(logs).doesNotContain(hash).doesNotContain(email);
        assertThat(OPAQUE_TOKEN.matcher(logs).find()).as("un valor con forma de token en el log").isFalse();
    }

    /** CA-07: email mal formado o ausente → 400 sobre email, sin token. */
    @Test
    void invalidOrMissingEmailIsRejectedWithoutCreatingTokens() throws Exception {
        int tokensBefore = tokenCount();

        recover("correo-sin-arroba")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.email")
                        .value("debe ser una dirección de correo electrónico con formato correcto"));
        postJson("/api/auth/password-recovery", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("no debe estar vacío"));

        assertThat(tokenCount()).isEqualTo(tokensBefore);
    }

    /** HU-006 T-06 y HU-007 T-04: rutas publicas; un Bearer invalido no las bloquea. */
    @Test
    void recoveryRoutesArePublicAndIgnoreAnInvalidBearer() throws Exception {
        mvc.perform(post("/api/auth/password-recovery").header(HttpHeaders.AUTHORIZATION, "Bearer basura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", uniqueEmail()))))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/password-reset").header(HttpHeaders.AUTHORIZATION, "Bearer basura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", randomToken(), "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    // ------------------------------------------------------------------ HU-007

    /** CA-01, CA-05, CA-06, CA-07: cambio correcto, login con la nueva y no con la anterior. */
    @Test
    void resetChangesThePasswordAndOnlyTheNewOneWorks() throws Exception {
        String email = uniqueEmail();
        long userId = register(email);
        String oldHash = passwordHash(userId);
        String token = seedToken(userId, Duration.ofMinutes(30));

        MvcResult result = reset(token, NEW_PASSWORD).andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();

        String newHash = passwordHash(userId);
        assertThat(newHash).isNotEqualTo(oldHash).isNotEqualTo(NEW_PASSWORD).startsWith("{bcrypt}$2");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, newHash)).isTrue();
        assertThat(tokenRow(token).get("used_at")).isNotNull();

        login(email, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        login(email, OLD_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    /** CA-02, CA-03, CA-04 y revocado: una sola respuesta para todo token no utilizable. */
    @Test
    void everyUnusableTokenGetsTheSameAnswerAndChangesNothing() throws Exception {
        String email = uniqueEmail();
        long userId = register(email);
        String used = seedToken(userId, Duration.ofMinutes(30));
        reset(used, NEW_PASSWORD).andExpect(status().isNoContent());
        String hashAfterFirstReset = passwordHash(userId);

        String expired = seedToken(userId, Duration.ofMinutes(-1));
        String revoked = seedToken(userId, Duration.ofMinutes(30));
        // Pedir uno nuevo revoca los anteriores sin usar: `revoked` queda revocado.
        recover(email).andExpect(status().isAccepted());
        assertThat(tokenRow(revoked).get("revoked_at")).isNotNull();
        assertThat(tokenRow(revoked).get("revoked_reason")).isEqualTo("SUPERSEDED");

        JsonNode reference = null;
        for (String token : List.of(used, randomToken(), expired, revoked)) {
            JsonNode answer = body(reset(token, "Otra-Clave#2028")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"))
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                    .andReturn());
            if (reference == null) {
                reference = answer;
            }
            assertThat(answer).isEqualTo(reference);
        }
        assertThat(passwordHash(userId)).isEqualTo(hashAfterFirstReset);
        assertThat(tokenRow(used).get("used_at")).isNotNull();
        assertThat(tokenRow(expired).get("used_at")).isNull();
    }

    /** CA-08: ausente, vacia, corta, sin numero o sin letra → 400 en newPassword; el token sigue vivo. */
    @Test
    void aPasswordOutsideThePolicyIsRejectedAndDoesNotConsumeTheToken() throws Exception {
        String email = uniqueEmail();
        long userId = register(email);
        String oldHash = passwordHash(userId);
        String token = seedToken(userId, Duration.ofMinutes(30));

        postJson("/api/auth/password-reset", Map.of("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("no debe estar vacío"));
        Map<String, String> weak = Map.of(
                "", "no debe estar vacío",
                "abc123", "debe tener al menos 8 caracteres",
                "abcdefgh", "debe combinar al menos una letra y un número",
                "12345678", "debe combinar al menos una letra y un número",
                "ñ".repeat(40) + "1", "no debe superar 72 bytes en UTF-8 (la ñ y las vocales con tilde ocupan 2 "
                        + "bytes; los emojis, 4)");
        for (Map.Entry<String, String> entry : weak.entrySet()) {
            reset(token, entry.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").value(entry.getValue()))
                    .andExpect(jsonPath("$.fieldErrors.password").doesNotExist());
        }

        assertThat(passwordHash(userId)).isEqualTo(oldHash);
        assertThat(tokenRow(token).get("used_at")).isNull();
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    /** D34: restablecer revoca TODAS las familias de refresh del usuario; su cookie deja de renovar. */
    @Test
    void resetRevokesEveryRefreshTokenFamilyOfTheUser() throws Exception {
        String email = uniqueEmail();
        long userId = register(email);
        String deviceA = refreshCookie(login(email, OLD_PASSWORD).andExpect(status().isOk()).andReturn());
        String deviceB = refreshCookie(login(email, OLD_PASSWORD).andExpect(status().isOk()).andReturn());
        String token = seedToken(userId, Duration.ofMinutes(30));

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        List<Map<String, Object>> sessions = jdbc.queryForList(
                "SELECT family_id, revoked_at, revoked_reason FROM refresh_tokens WHERE user_id = ?", userId);
        assertThat(sessions).hasSize(2).allSatisfy(t -> {
            assertThat(t.get("revoked_at")).isNotNull();
            assertThat(t.get("revoked_reason")).isEqualTo("PASSWORD_RESET");
        });
        assertThat(sessions.stream().map(t -> t.get("family_id")).distinct()).hasSize(2);
        for (String cookie : List.of(deviceA, deviceB)) {
            mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fcv_refresh", cookie)))
                    .andExpect(status().isUnauthorized());
        }
    }

    /** CA-09: ni el token ni la contraseña nueva llegan al log, ni al acertar ni al fallar. */
    @Test
    void neitherTheTokenNorThePasswordReachTheLog(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        long userId = register(email);
        String token = seedToken(userId, Duration.ofMinutes(30));
        String unknown = randomToken();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(unknown, "Rechazada#2029").andExpect(status().isBadRequest());
        reset(token, "Reusada#2030").andExpect(status().isBadRequest());

        assertThat(output.getAll())
                .doesNotContain(token)
                .doesNotContain(unknown)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain("Rechazada#2029")
                .doesNotContain("Reusada#2030")
                .doesNotContain(RefreshTokenHasher.sha256Hex(token));
    }

    private static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("fcv_refresh");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
