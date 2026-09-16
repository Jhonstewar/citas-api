package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.auth.RefreshTokenHasher;

/**
 * Integracion REST + seguridad + MySQL real (contenedor {@code mysql}) del slice HU-001..HU-004.
 * Cada prueba usa emails {@code @it.fcv.test} y documentos aleatorios, y los borra al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthFlowIntegrationTest {

    private static final String DOMAIN = "@it.fcv.test";
    private static final String PASSWORD = "Clave-Secreta#2026";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtEncoder jwtEncoder;

    @AfterEach
    void cleanUp() {
        // refresh_tokens y user_roles caen por ON DELETE CASCADE.
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%" + DOMAIN);
    }

    // ------------------------------------------------------------------ helpers

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
    }

    private static String uniqueDocument() {
        return "IT" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }

    private static Map<String, Object> registerBody(String email, String document) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstNames", "Ana María");
        body.put("lastNames", "Pérez Gómez");
        body.put("documentType", "CC");
        body.put("documentNumber", document);
        body.put("email", email);
        body.put("phone", "3001234567");
        body.put("password", PASSWORD);
        return body;
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode register(String email, String document) throws Exception {
        String response = postJson("/api/auth/register", registerBody(email, document))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private JsonNode login(String email) throws Exception {
        String response = postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    // ------------------------------------------------------------------ HU-001 registro

    @Test
    void registerCreatesUserWithRoleUserAndStoresOnlyPasswordHash(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        String document = uniqueDocument();

        String raw = postJson("/api/auth/register", registerBody(email, document))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.documentType").value("CC"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$", not(hasKey("password"))))
                .andExpect(jsonPath("$", not(hasKey("passwordHash"))))
                .andReturn().getResponse().getContentAsString();

        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class,
                email);
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("{bcrypt}$2");
        assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();
        assertThat(raw).doesNotContain(PASSWORD).doesNotContain(storedHash);
        assertThat(jdbc.queryForList("""
                SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                JOIN users u ON u.id = ur.user_id WHERE u.email = ?""", String.class, email))
                .containsExactly("USER");
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(storedHash);
    }

    @Test
    void registerAcceptsAliasFieldNames() throws Exception {
        Map<String, Object> body = registerBody(uniqueEmail(), uniqueDocument());
        body.put("firstName", body.remove("firstNames"));
        body.put("lastName", body.remove("lastNames"));
        body.put("documentTypeCode", body.remove("documentType"));

        postJson("/api/auth/register", body).andExpect(status().isCreated());
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String otherDocument = uniqueDocument();

        postJson("/api/auth/register", registerBody(email.toUpperCase().replace(DOMAIN.toUpperCase(), DOMAIN),
                otherDocument))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("El email ya está registrado"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE document_number = ?", Integer.class,
                otherDocument)).isZero();
    }

    @Test
    void duplicateDocumentReturns409() throws Exception {
        String document = uniqueDocument();
        register(uniqueEmail(), document);
        String otherEmail = uniqueEmail();

        postJson("/api/auth/register", registerBody(otherEmail, document))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El documento ya está registrado"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, otherEmail))
                .isZero();
    }

    @Test
    void missingFieldsReturn400WithFieldErrors(CapturedOutput output) throws Exception {
        postJson("/api/auth/register", Map.of("password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.firstNames").exists())
                .andExpect(jsonPath("$.fieldErrors.lastNames").exists())
                .andExpect(jsonPath("$.fieldErrors.documentType").exists())
                .andExpect(jsonPath("$.fieldErrors.documentNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void invalidEmailFormatReturns400AndPersistsNothing(CapturedOutput output) throws Exception {
        String document = uniqueDocument();
        String response = postJson("/api/auth/register", registerBody("correo-sin-arroba", document))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(PASSWORD);
        assertThat(output.getAll()).doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE document_number = ?", Integer.class,
                document)).isZero();
    }

    @Test
    void unknownDocumentTypeReturns400() throws Exception {
        Map<String, Object> body = registerBody(uniqueEmail(), uniqueDocument());
        body.put("documentType", "ZZ");

        postJson("/api/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.documentType").exists());
    }

    // ------------------------------------------------------------------ HU-002 login

    @Test
    void loginReturnsBothTokensWithIdentityAndRoles(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        long userId = register(email, uniqueDocument()).get("id").asLong();

        JsonNode tokens = login(email);
        String access = tokens.get("accessToken").asText();
        String refresh = tokens.get("refreshToken").asText();

        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(access).isNotBlank().isNotEqualTo(refresh);
        assertThat(tokens.toString()).doesNotContain(PASSWORD);

        Jwt jwt = jwtDecoder.decode(access);
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT token_hash, expires_at FROM refresh_tokens WHERE user_id = ?", userId);
        assertThat(row.get("token_hash")).isNotEqualTo(refresh).isEqualTo(RefreshTokenHasher.sha256Hex(refresh));
        Instant refreshExpiry = ((java.time.LocalDateTime) row.get("expires_at"))
                .atZone(java.time.ZoneId.of("America/Bogota")).toInstant();
        assertThat(jwt.getExpiresAt()).isBefore(refreshExpiry);

        me(access).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$", not(hasKey("passwordHash"))));

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(access).doesNotContain(refresh);
    }

    @Test
    void invalidCredentialsReturnSame401ForUnknownEmailAndWrongPassword() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());

        String unknown = postJson("/api/auth/login", Map.of("email", uniqueEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String wrongPassword = postJson("/api/auth/login", Map.of("email", email, "password", "otra-clave"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        JsonNode a = json.readTree(unknown);
        JsonNode b = json.readTree(wrongPassword);
        assertThat(a.get("detail")).isEqualTo(b.get("detail"));
        assertThat(a.get("title")).isEqualTo(b.get("title"));
        assertThat(unknown).doesNotContain("accessToken");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?""",
                Integer.class, email)).isZero();
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        jdbc.update("UPDATE users SET active = FALSE WHERE email = ?", email);

        postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void protectedEndpointRejectsMissingMalformedAndExpiredTokens() throws Exception {
        String email = uniqueEmail();
        long userId = register(email, uniqueDocument()).get("id").asLong();

        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        me("esto-no-es-un-jwt").andExpect(status().isUnauthorized());

        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("citas-api").subject(String.valueOf(userId))
                .issuedAt(past).expiresAt(past.plusSeconds(900)).claim("roles", List.of("USER")).build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
        me(expired).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ HU-003 refresh

    @Test
    void refreshRotatesTokenAndRenewedAccessTokenWorks() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        JsonNode first = login(email);
        String oldRefresh = first.get("refreshToken").asText();

        String body = refresh(oldRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        JsonNode second = json.readTree(body);
        String newRefresh = second.get("refreshToken").asText();
        String newAccess = second.get("accessToken").asText();

        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(newAccess).isNotEqualTo(first.get("accessToken").asText());
        assertThat(jwtDecoder.decode(newAccess).getExpiresAt())
                .isAfterOrEqualTo(jwtDecoder.decode(first.get("accessToken").asText()).getExpiresAt());

        Map<String, Object> old = jdbc.queryForMap(
                "SELECT used_at, replaced_by_token_id, family_id FROM refresh_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(oldRefresh));
        Map<String, Object> fresh = jdbc.queryForMap(
                "SELECT id, family_id, used_at FROM refresh_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(newRefresh));
        assertThat(old.get("used_at")).isNotNull();
        assertThat(((Number) old.get("replaced_by_token_id")).longValue())
                .isEqualTo(((Number) fresh.get("id")).longValue());
        assertThat(fresh.get("family_id")).isEqualTo(old.get("family_id"));
        assertThat(fresh.get("used_at")).isNull();

        me(newAccess).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void reusingOldRefreshTokenReturns401AndRevokesFamily() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String oldRefresh = login(email).get("refreshToken").asText();
        String newRefresh = json.readTree(refresh(oldRefresh).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        refresh(oldRefresh).andExpect(status().isUnauthorized());

        List<Map<String, Object>> family = jdbc.queryForList("""
                SELECT t.revoked_at, t.revoked_reason FROM refresh_tokens t JOIN users u ON u.id = t.user_id
                WHERE u.email = ?""", email);
        assertThat(family).hasSize(2).allSatisfy(t -> {
            assertThat(t.get("revoked_at")).isNotNull();
            assertThat(t.get("revoked_reason")).isEqualTo("REUSE_DETECTED");
        });
        refresh(newRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAndExpiredRefreshTokensReturnSame401() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String token = login(email).get("refreshToken").asText();
        jdbc.update("UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), RefreshTokenHasher.sha256Hex(token));

        String expired = refresh(token).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = refresh("desconocido-" + UUID.randomUUID()).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(expired).get("detail")).isEqualTo(json.readTree(unknown).get("detail"));
    }

    // ------------------------------------------------------------------ HU-004 logout

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String refreshToken = login(email).get("refreshToken").asText();

        postJson("/api/auth/logout", Map.of("refreshToken", refreshToken)).andExpect(status().isNoContent());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT revoked_at, revoked_reason FROM refresh_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(refreshToken));
        assertThat(row.get("revoked_at")).isNotNull();
        assertThat(row.get("revoked_reason")).isEqualTo("LOGOUT");

        refresh(refreshToken).andExpect(status().isUnauthorized());

        postJson("/api/auth/logout", Map.of("refreshToken", refreshToken)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForMap("SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(refreshToken)).get("revoked_at")).isEqualTo(row.get("revoked_at"));
        postJson("/api/auth/logout", Map.of("refreshToken", "inexistente-" + UUID.randomUUID()))
                .andExpect(status().isNoContent());

        assertThat(output.getAll()).doesNotContain(refreshToken);
    }

    // ------------------------------------------------------------------ CORS

    @Test
    void corsPreflightAllowsFrontendOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
