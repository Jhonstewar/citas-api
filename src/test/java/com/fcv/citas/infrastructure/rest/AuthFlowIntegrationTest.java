package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

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
    private static final String INVALID_DATA_TITLE = "Datos inválidos";
    private static final String UNREADABLE_BODY_DETAIL = "El cuerpo de la petición falta o no es un JSON válido";
    private static final String PASSWORD_TOO_LONG_MESSAGE =
            "no debe superar 72 bytes en UTF-8 (la ñ y las vocales con tilde ocupan 2 bytes; los emojis, 4)";
    private static final String WRONG_PASSWORD = "Otra-Clave#9999";
    private static final String REFRESH_COOKIE = "fcv_refresh";
    /** JWT_REFRESH_DAYS=7 del perfil de pruebas, en segundos. */
    private static final long REFRESH_MAX_AGE = Duration.ofDays(7).toSeconds();

    /**
     * Reloj de la aplicacion durante estas pruebas (sustituye al bean {@code clock} de
     * {@code UseCaseConfig}). Sin intervencion sigue al reloj del sistema; solo las pruebas de
     * HU-003 CA-01 lo congelan y lo adelantan, y se restablece despues de cada prueba.
     */
    private static final AdjustableClock CLOCK = new AdjustableClock();

    @TestBean(methodName = "adjustableClock")
    private Clock applicationClock;

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

    static Clock adjustableClock() {
        return CLOCK;
    }

    @AfterEach
    void cleanUp() {
        CLOCK.reset();
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

    /** Todas las respuestas de error, de la cadena de seguridad o del advice, son RFC 9457. */
    private static ResultMatcher problemJson() {
        return content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions postJsonWithBearer(String path, Object body, String bearer) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions postRaw(String path, String rawBody) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(rawBody));
    }

    private JsonNode register(String email, String document) throws Exception {
        String response = postJson("/api/auth/register", registerBody(email, document))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private MvcResult loginResult(String email) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
    }

    /** Refresh token que el login dejo en la cookie {@code fcv_refresh} (D36). */
    private String loginRefreshToken(String email) throws Exception {
        return refreshCookieValue(loginResult(email));
    }

    /** Unica cabecera {@code Set-Cookie} de {@code fcv_refresh} de la respuesta. */
    private static String refreshSetCookie(MvcResult result) {
        List<String> headers = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(REFRESH_COOKIE + "=")).toList();
        assertThat(headers).as("una sola cabecera Set-Cookie de %s", REFRESH_COOKIE).hasSize(1);
        return headers.getFirst();
    }

    private static String refreshCookieValue(MvcResult result) {
        String header = refreshSetCookie(result);
        return header.substring(REFRESH_COOKIE.length() + 1, header.indexOf(';'));
    }

    /** Atributos de la cookie sin {@code Expires}, que depende del reloj: el resto es exacto. */
    private static Set<String> refreshCookieAttributes(MvcResult result) {
        String[] parts = refreshSetCookie(result).split(";\\s*");
        return Arrays.stream(parts).skip(1).filter(p -> !p.startsWith("Expires=")).collect(Collectors.toSet());
    }

    /** Cookie que el servidor manda borrar: vacia, Max-Age=0 y los mismos Path/HttpOnly/Secure/SameSite. */
    private static void assertRefreshCookieCleared(MvcResult result) {
        assertThat(refreshCookieValue(result)).isEmpty();
        assertThat(refreshCookieAttributes(result))
                .containsExactlyInAnyOrder("Path=/api/auth", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict");
    }

    private ResultActions registerWithPassword(String email, String document, String password) throws Exception {
        Map<String, Object> body = registerBody(email, document);
        body.put("password", password);
        return postJson("/api/auth/register", body);
    }

    private ResultActions loginAttempt(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
    }

    private int refreshTokenCount(String email) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?""",
                Integer.class, email);
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Contraseñas de mas de 72 bytes en UTF-8. Las cuatro primeras caben en {@code @Size(max = 72)}
     * porque cuentan caracteres (unidades UTF-16), no bytes; la ultima ni siquiera cabe en caracteres.
     */
    static Stream<Arguments> passwordsOver72Utf8Bytes() {
        return Stream.of(
                Arguments.of("40 x ñ: 40 caracteres, 80 bytes", "ñ".repeat(40)),
                Arguments.of("36 x ñ + 'a': 37 caracteres, 73 bytes", "ñ".repeat(36) + "a"),
                Arguments.of("25 x €: 25 caracteres, 75 bytes", "€".repeat(25)),
                Arguments.of("19 emojis: 38 caracteres, 76 bytes", "😀".repeat(19)),
                Arguments.of("73 x 'a': 73 caracteres, 73 bytes", "a".repeat(73)));
    }

    /**
     * Contraseñas de EXACTAMENTE 72 bytes en UTF-8: el maximo que BCrypt usa entero. Desde D29
     * llevan letra y numero para cumplir la politica (antes eran 36 x ñ, 18 emojis y 72 x 'a');
     * lo que se prueba —el limite exacto en bytes— no cambia.
     */
    static Stream<Arguments> passwordsOfExactly72Utf8Bytes() {
        return Stream.of(
                Arguments.of("35 x ñ + 'a1'", "ñ".repeat(35) + "a1"),
                Arguments.of("17 emojis + 'a1b2'", "😀".repeat(17) + "a1b2"),
                Arguments.of("71 x 'a' + '1'", "a".repeat(71) + "1"));
    }

    /** D29: contraseñas que el registro rechaza con el mensaje de cada regla. */
    static Stream<Arguments> passwordsOutsideThePolicy() {
        return Stream.of(
                Arguments.of("abc123", "debe tener al menos 8 caracteres"),
                Arguments.of("abcdefgh", "debe combinar al menos una letra y un número"),
                Arguments.of("12345678", "debe combinar al menos una letra y un número"),
                Arguments.of("ññññññññ", "debe combinar al menos una letra y un número"));
    }

    /** D36: sin cuerpo, con el refresh token en la cookie. */
    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/logout").cookie(new Cookie(REFRESH_COOKIE, refreshToken)));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** Claims validos de un access token para {@code userId}, con el emisor indicado. */
    private static JwtClaimsSet accessClaims(String issuer, long userId, Instant issuedAt) {
        return JwtClaimsSet.builder().issuer(issuer).subject(String.valueOf(userId))
                .issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(900))
                .claim("roles", List.of("USER")).build();
    }

    private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Firmado con la clave buena y emisor correcto, pero vencido hace 45 minutos. */
    private String expiredAccessToken(long userId) {
        return sign(jwtEncoder, accessClaims("citas-api", userId, Instant.now().minusSeconds(3600)));
    }

    private List<Map<String, Object>> refreshTokensTable() {
        return jdbc.queryForList("SELECT * FROM refresh_tokens ORDER BY id");
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
                .andExpect(problemJson())
                .andExpect(jsonPath("$.title").value(INVALID_DATA_TITLE))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.firstNames").value("no debe estar vacío"))
                .andExpect(jsonPath("$.fieldErrors.lastNames").exists())
                .andExpect(jsonPath("$.fieldErrors.documentType").exists())
                .andExpect(jsonPath("$.fieldErrors.documentNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.password").value("no debe estar vacío"));
    }

    @Test
    void invalidEmailFormatReturns400AndPersistsNothing(CapturedOutput output) throws Exception {
        String document = uniqueDocument();
        String response = postJson("/api/auth/register", registerBody("correo-sin-arroba", document))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email")
                        .value("debe ser una dirección de correo electrónico con formato correcto"))
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

    // BCrypt trabaja sobre los BYTES UTF-8 y solo usa los 72 primeros. Una regla en caracteres
    // deja pasar contraseñas como 40 x ñ (80 bytes), que BCrypt se niega a hashear.

    @ParameterizedTest(name = "{0}")
    @MethodSource("passwordsOver72Utf8Bytes")
    void registerRejectsPasswordOver72Utf8BytesWith400FieldError(String description, String password,
            CapturedOutput output) throws Exception {
        assertThat(utf8Bytes(password)).isGreaterThan(72);
        String email = uniqueEmail();

        String response = registerWithPassword(email, uniqueDocument(), password)
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value(INVALID_DATA_TITLE))
                .andExpect(jsonPath("$.fieldErrors.password").value(PASSWORD_TOO_LONG_MESSAGE))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(response).doesNotContain(password);
        assertThat(output.getAll()).doesNotContain(password);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email))
                .isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("passwordsOfExactly72Utf8Bytes")
    void registerAcceptsPasswordOfExactly72Utf8BytesAndItWorksForLogin(String description, String password)
            throws Exception {
        assertThat(utf8Bytes(password)).isEqualTo(72);
        String email = uniqueEmail();

        registerWithPassword(email, uniqueDocument(), password).andExpect(status().isCreated());

        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class,
                email);
        assertThat(passwordEncoder.matches(password, storedHash)).isTrue();
        loginAttempt(email, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("passwordsOutsideThePolicy")
    void registerRejectsPasswordsOutsideThePolicy(String password, String message, CapturedOutput output)
            throws Exception {
        String email = uniqueEmail();

        String response = registerWithPassword(email, uniqueDocument(), password)
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.title").value(INVALID_DATA_TITLE))
                .andExpect(jsonPath("$.fieldErrors.password").value(message))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(response).doesNotContain(password);
        assertThat(output.getAll()).doesNotContain(password);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email))
                .isZero();
    }

    @Test
    void registerAcceptsAnyUnicodeLetterForThePolicy() throws Exception {
        // La ñ y las vocales con tilde son letras (\p{L}): sin ninguna letra ASCII sigue cumpliendo.
        registerWithPassword(uniqueEmail(), uniqueDocument(), "ñáéíóú2026").andExpect(status().isCreated());
    }

    /** D29 solo se aplica al FIJAR una contraseña: una cuenta anterior con una clave corta sigue entrando. */
    @Test
    void loginDoesNotApplyThePolicyToExistingAccounts() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String legacy = "corta";
        jdbc.update("UPDATE users SET password_hash = ? WHERE email = ?", passwordEncoder.encode(legacy), email);

        loginAttempt(email, legacy).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ------------------------------------------------------------------ Errores de entrada comunes

    @Test
    void validationMessagesAreSpanishWithOrWithoutAcceptLanguage() throws Exception {
        String blank = json.writeValueAsString(Map.of("email", "", "password", ""));

        // Sin Accept-Language: antes salia el idioma de la JVM del contenedor ("must not be blank").
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(blank))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("no debe estar vacío"))
                .andExpect(jsonPath("$.fieldErrors.password").value("no debe estar vacío"))
                .andReturn();
        assertThat(result.getRequest().getHeader(HttpHeaders.ACCEPT_LANGUAGE)).isNull();
        JsonNode withoutHeader = body(result);

        // Un cliente que pide otro idioma recibe exactamente la misma respuesta.
        for (String language : List.of("en-US", "fr-FR", "pt-BR")) {
            JsonNode withHeader = body(mvc.perform(post("/api/auth/login")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, language)
                            .contentType(MediaType.APPLICATION_JSON).content(blank))
                    .andExpect(status().isBadRequest())
                    .andReturn());
            assertThat(withHeader).as("Accept-Language: %s", language).isEqualTo(withoutHeader);
        }
    }

    @Test
    void malformedJsonReturnsTheSame400ProblemWithoutParserDetails(CapturedOutput output) throws Exception {
        // JSON cortado y con la contraseña sin comillas: el mensaje de Jackson citaria el token
        // que no supo leer. Ni la respuesta ni el log pueden contenerlo.
        String malformed = "{\"email\": \"alguien" + DOMAIN + "\", \"password\": " + PASSWORD;

        String response = postRaw("/api/auth/login", malformed)
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value(INVALID_DATA_TITLE))
                .andExpect(jsonPath("$.detail").value(UNREADABLE_BODY_DETAIL))
                .andExpect(jsonPath("$", not(hasKey("fieldErrors"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(response)
                .doesNotContain("Failed to read request")
                .doesNotContain("JSON parse error")
                .doesNotContain("Unrecognized")
                .doesNotContain("com.fasterxml")
                .doesNotContain("Clave");
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("Clave-Secreta");
    }

    /**
     * Antes se probaba sobre {@code /api/auth/logout}; desde D36 el logout no tiene cuerpo (lee la
     * cookie), asi que el mismo contrato de 400 se fija sobre el login, que sigue exigiendolo.
     */
    @Test
    void missingOrMistypedBodyReturnsTheSame400Problem() throws Exception {
        // Cuerpo vacio con Content-Type JSON.
        JsonNode empty = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andReturn());
        // Sin cuerpo y sin Content-Type.
        JsonNode absent = body(mvc.perform(post("/api/auth/login"))
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andReturn());
        // JSON valido, pero un objeto donde se espera un texto.
        JsonNode mistyped = body(postRaw("/api/auth/login", "{\"email\": {\"valor\": 1}, \"password\": \"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(problemJson())
                .andReturn());

        assertThat(empty.get("title").asText()).isEqualTo(INVALID_DATA_TITLE);
        assertThat(empty.get("detail").asText()).isEqualTo(UNREADABLE_BODY_DETAIL);
        assertThat(absent).isEqualTo(empty);
        assertThat(mistyped).isEqualTo(empty);
        // "Required request body is missing: void com.fcv..." no debe asomar.
        assertThat(empty.toString()).doesNotContain("Required").doesNotContain("com.fcv");
    }

    // ------------------------------------------------------------------ HU-002 login

    @Test
    void loginReturnsBothTokensWithIdentityAndRoles(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        long userId = register(email, uniqueDocument()).get("id").asLong();

        MvcResult result = loginResult(email);
        JsonNode tokens = body(result);
        String access = tokens.get("accessToken").asText();
        // D36: el refresh token sale solo en la cookie; el cuerpo ya no lo lleva.
        String refresh = refreshCookieValue(result);
        assertThat(tokens.has("refreshToken")).isFalse();

        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(access).isNotBlank().isNotEqualTo(refresh);
        assertThat(tokens.toString()).doesNotContain(PASSWORD);

        Jwt jwt = jwtDecoder.decode(access);
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        // El token va firmado, no cifrado: su carga util la lee cualquiera. Solo lleva lo que
        // alguien consume de verdad, y el email no lo consume nadie.
        assertThat(jwt.getClaims()).doesNotContainKey("email");
        assertThat(access).doesNotContain(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(email.getBytes(StandardCharsets.UTF_8)));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT token_hash, expires_at FROM refresh_tokens WHERE user_id = ?", userId);
        assertThat(row.get("token_hash")).isNotEqualTo(refresh).isEqualTo(RefreshTokenHasher.sha256Hex(refresh));
        Instant refreshExpiry = ((java.time.LocalDateTime) row.get("expires_at"))
                .atZone(ZoneId.of("America/Bogota")).toInstant();
        assertThat(jwt.getExpiresAt()).isBefore(refreshExpiry);

        me(access).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$", not(hasKey("passwordHash"))));

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(access).doesNotContain(refresh);
    }

    @Test
    void invalidCredentialsReturnSame401ForUnknownEmailAndWrongPassword(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String wrongPassword = "Otra-Clave#9999";

        MvcResult unknownEmail = postJson("/api/auth/login", Map.of("email", uniqueEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andReturn();
        MvcResult badPassword = postJson("/api/auth/login", Map.of("email", email, "password", wrongPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andReturn();

        // CA-03: los cuerpos COMPLETOS son identicos —no solo title y detail—, igual que el tipo
        // de contenido. Ningun campo extra puede delatar cual de los dos datos fallo.
        JsonNode a = body(unknownEmail);
        JsonNode b = body(badPassword);
        assertThat(a).isEqualTo(b);
        assertThat(a.get("detail").asText()).isEqualTo("Credenciales inválidas");
        assertThat(unknownEmail.getResponse().getContentType())
                .isEqualTo(badPassword.getResponse().getContentType());
        assertThat(a.toString()).doesNotContain("accessToken").doesNotContain("refreshToken");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?""",
                Integer.class, email)).isZero();

        // CA-08: tampoco el login FALLIDO deja la contraseña en el log (ni la buena ni la mala).
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(wrongPassword);
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("passwordsOver72Utf8Bytes")
    void loginWithPasswordOver72Utf8BytesReturnsTheSame401AsAWrongPassword(String description, String password,
            CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());

        JsonNode wrongPassword = body(loginAttempt(email, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn());
        MvcResult tooLong = loginAttempt(email, password)
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andReturn();
        MvcResult tooLongUnknownEmail = loginAttempt(uniqueEmail(), password)
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andReturn();

        // Mismo cuerpo completo que cualquier credencial invalida: ni un 400 que delate la regla
        // de longitud, ni un 500, ni nada que distinga un email existente de uno inventado.
        assertThat(body(tooLong)).isEqualTo(wrongPassword);
        assertThat(body(tooLongUnknownEmail)).isEqualTo(wrongPassword);
        assertThat(wrongPassword.get("detail").asText()).isEqualTo("Credenciales inválidas");
        assertThat(refreshTokenCount(email)).isZero();
        assertThat(output.getAll()).doesNotContain(password);
    }

    /**
     * El caso de CVE-2025-22228 por el lado del login: {@code BCrypt.checkpw} no rechaza las
     * contraseñas de mas de 72 bytes, las TRUNCA. Sin una comprobacion propia, cualquier valor que
     * empiece por los 72 bytes de la contraseña real abriria la sesion.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("passwordsOfExactly72Utf8Bytes")
    void loginRejectsPasswordThatOnlySharesTheFirst72BytesWithTheRealOne(String description, String realPassword)
            throws Exception {
        String email = uniqueEmail();
        registerWithPassword(email, uniqueDocument(), realPassword).andExpect(status().isCreated());
        // Control: la contraseña real SI entra, asi que los 401 siguientes solo se deben al sufijo.
        loginAttempt(email, realPassword).andExpect(status().isOk());
        int tokensAfterRealLogin = refreshTokenCount(email);

        JsonNode wrongPassword = body(loginAttempt(email, WRONG_PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn());
        for (String extended : List.of(realPassword + "x", realPassword + "ñ", realPassword + "-lo-que-sea")) {
            MvcResult result = loginAttempt(email, extended)
                    .andExpect(status().isUnauthorized())
                    .andExpect(problemJson())
                    .andReturn();
            assertThat(body(result)).as("login con %d bytes", utf8Bytes(extended)).isEqualTo(wrongPassword);
        }
        assertThat(refreshTokenCount(email)).isEqualTo(tokensAfterRealLogin);
    }

    @Test
    void protectedEndpointRejectsMissingMalformedAndExpiredTokens() throws Exception {
        String email = uniqueEmail();
        long userId = register(email, uniqueDocument()).get("id").asLong();

        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"));
        me("esto-no-es-un-jwt").andExpect(status().isUnauthorized()).andExpect(problemJson());
        me(expiredAccessToken(userId)).andExpect(status().isUnauthorized()).andExpect(problemJson());
    }

    @Test
    void protectedEndpointRejectsWellFormedTokenSignedWithAnotherKeyOrIssuer() throws Exception {
        long userId = register(uniqueEmail(), uniqueDocument()).get("id").asLong();
        JwtClaimsSet claims = accessClaims("citas-api", userId, Instant.now());

        // Control: los mismos claims firmados con la clave de la aplicacion SI se aceptan. Asi el
        // 401 siguiente solo puede deberse a la firma.
        me(sign(jwtEncoder, claims)).andExpect(status().isOk());

        // Clave HS256 aleatoria de 256 bits: JWT perfectamente formado, firma de otro.
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(new SecretKeySpec(otherKey, "HmacSHA256")));
        String forged = sign(foreignEncoder, claims);
        assertThat(forged.split("\\.")).hasSize(3);
        me(forged).andExpect(status().isUnauthorized()).andExpect(problemJson());

        // Clave correcta pero otro emisor: tambien se rechaza (JwtIssuerValidator).
        me(sign(jwtEncoder, accessClaims("otro-emisor", userId, Instant.now())))
                .andExpect(status().isUnauthorized()).andExpect(problemJson());
    }

    // ------------------------------------------------------------------ Bearer en rutas publicas

    @Test
    void logoutWithGarbageBearerStillRevokesTheFamily() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String first = loginRefreshToken(email);
        String current = refreshCookieValue(refresh(first).andExpect(status().isOk()).andReturn());

        // Antes: 401 "Se requiere un access token valido" y el logout no revocaba nada.
        mvc.perform(post("/api/auth/logout").cookie(new Cookie(REFRESH_COOKIE, current))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer basura"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> family = jdbc.queryForList("""
                SELECT t.revoked_at, t.revoked_reason FROM refresh_tokens t JOIN users u ON u.id = t.user_id
                WHERE u.email = ?""", email);
        assertThat(family).hasSize(2).allSatisfy(t -> {
            assertThat(t.get("revoked_at")).isNotNull();
            assertThat(t.get("revoked_reason")).isEqualTo("LOGOUT");
        });
        refresh(current).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithExpiredBearerSucceeds() throws Exception {
        String email = uniqueEmail();
        long userId = register(email, uniqueDocument()).get("id").asLong();

        MvcResult result = postJsonWithBearer("/api/auth/login", Map.of("email", email, "password", PASSWORD),
                expiredAccessToken(userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        // D36: el refresh token ya no va en el cuerpo, sino en la cookie.
        assertThat(refreshCookieValue(result)).isNotEmpty();
    }

    @Test
    void registerAndRefreshIgnoreAnInvalidBearer() throws Exception {
        String email = uniqueEmail();
        long userId = body(postJsonWithBearer("/api/auth/register", registerBody(email, uniqueDocument()), "basura")
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
        String refreshToken = loginRefreshToken(email);

        // El caso real: el access token ya vencio y el cliente llama a refresh para renovarlo.
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshToken))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void protectedRoutesStillRejectAnInvalidBearer() throws Exception {
        long userId = register(uniqueEmail(), uniqueDocument()).get("id").asLong();

        me("basura")
                .andExpect(status().isUnauthorized())
                .andExpect(problemJson())
                .andExpect(jsonPath("$.detail").value("Se requiere un access token válido"));
        me(expiredAccessToken(userId)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ HU-003 refresh

    @Test
    void refreshRotatesTokenAndRenewedAccessTokenWorks(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        assertThat(applicationClock).as("la aplicacion usa el reloj de la prueba").isSameAs(CLOCK);

        // Reloj congelado: login y renovacion ocurren en instantes exactos y conocidos, sin
        // depender de lo que tarde cada peticion. En el pasado para que ningun token nazca con un
        // `iat` posterior al reloj real con el que el decoder valida `exp`.
        CLOCK.freezeAt(Instant.now().minus(Duration.ofMinutes(2)));
        MvcResult loginResult = loginResult(email);
        String oldAccess = body(loginResult).get("accessToken").asText();
        String oldRefresh = refreshCookieValue(loginResult);

        Duration elapsed = Duration.ofMinutes(1);
        CLOCK.advance(elapsed);
        MvcResult refreshed = refresh(oldRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        JsonNode second = body(refreshed);
        // D36: la renovacion ROTA la cookie; el cuerpo solo trae el access token.
        String newRefresh = refreshCookieValue(refreshed);
        assertThat(second.has("refreshToken")).isFalse();
        assertThat(refreshCookieAttributes(refreshed)).containsExactlyInAnyOrder("Path=/api/auth",
                "Max-Age=" + REFRESH_MAX_AGE, "Secure", "HttpOnly", "SameSite=Strict");
        String newAccess = second.get("accessToken").asText();

        // HU-003 CA-01: access token nuevo, distinto, y con expiracion ESTRICTAMENTE posterior;
        // posterior, ademas, exactamente en lo que avanzo el reloj.
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(newAccess).isNotEqualTo(oldAccess);
        Instant oldExpiry = jwtDecoder.decode(oldAccess).getExpiresAt();
        Instant newExpiry = jwtDecoder.decode(newAccess).getExpiresAt();
        assertThat(newExpiry).isAfter(oldExpiry).isEqualTo(oldExpiry.plus(elapsed));

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

        // HU-003 CA-08: una renovacion CORRECTA no deja en el log ni el refresh presentado ni
        // ninguno de los tokens emitidos.
        assertThat(output.getAll())
                .doesNotContain(oldRefresh)
                .doesNotContain(newRefresh)
                .doesNotContain(oldAccess)
                .doesNotContain(newAccess);
    }

    @Test
    void refreshInTheSameInstantStillIssuesADistinctAccessToken() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        CLOCK.freezeAt(Instant.now().minus(Duration.ofMinutes(1)));

        MvcResult firstResult = loginResult(email);
        JsonNode first = body(firstResult);
        JsonNode second = body(refresh(refreshCookieValue(firstResult)).andExpect(status().isOk()).andReturn());

        Jwt oldJwt = jwtDecoder.decode(first.get("accessToken").asText());
        Jwt newJwt = jwtDecoder.decode(second.get("accessToken").asText());
        // Sin que pase el tiempo, `exp` coincide (granularidad de segundos): la distincion la da
        // el `jti` aleatorio, no el reloj.
        assertThat(newJwt.getExpiresAt()).isEqualTo(oldJwt.getExpiresAt());
        assertThat(newJwt.getId()).isNotBlank().isNotEqualTo(oldJwt.getId());
        assertThat(newJwt.getTokenValue()).isNotEqualTo(oldJwt.getTokenValue());
    }

    @Test
    void reusingOldRefreshTokenReturns401AndRevokesFamily(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String oldRefresh = loginRefreshToken(email);
        String newRefresh = refreshCookieValue(refresh(oldRefresh).andExpect(status().isOk()).andReturn());

        // El reuso tambien borra la cookie que se presento.
        assertRefreshCookieCleared(refresh(oldRefresh).andExpect(status().isUnauthorized())
                .andExpect(problemJson()).andReturn());

        List<Map<String, Object>> family = jdbc.queryForList("""
                SELECT t.revoked_at, t.revoked_reason FROM refresh_tokens t JOIN users u ON u.id = t.user_id
                WHERE u.email = ?""", email);
        assertThat(family).hasSize(2).allSatisfy(t -> {
            assertThat(t.get("revoked_at")).isNotNull();
            assertThat(t.get("revoked_reason")).isEqualTo("REUSE_DETECTED");
        });
        refresh(newRefresh).andExpect(status().isUnauthorized());

        // HU-003 CA-08, lado rechazado: tampoco una renovacion denegada registra el token.
        assertThat(output.getAll()).doesNotContain(oldRefresh).doesNotContain(newRefresh);
    }

    @Test
    void unknownAndExpiredRefreshTokensReturnSame401() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String token = loginRefreshToken(email);
        jdbc.update("UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), RefreshTokenHasher.sha256Hex(token));

        MvcResult expired = refresh(token).andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknown = refresh("desconocido-" + UUID.randomUUID()).andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult absent = mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(problemJson()).andReturn();
        MvcResult tooLong = refresh("x".repeat(257)).andExpect(status().isUnauthorized()).andReturn();

        // Cuerpos completos identicos: nada distingue un token que existio de uno inventado, ni
        // de una cookie ausente (D36). Y en todos los casos la cookie se manda borrar.
        assertThat(body(expired)).isEqualTo(body(unknown)).isEqualTo(body(absent)).isEqualTo(body(tooLong));
        assertThat(body(absent).get("detail").asText()).isEqualTo("La sesión no es válida o ha expirado");
        for (MvcResult result : List.of(expired, unknown, absent, tooLong)) {
            assertRefreshCookieCleared(result);
        }
    }

    // ------------------------------------------------------------------ D36 cookie del refresh token

    @Test
    void loginSetsTheRefreshCookieWithExactAttributes(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        long userId = register(email, uniqueDocument()).get("id").asLong();

        MvcResult result = loginResult(email);
        String token = refreshCookieValue(result);

        assertThat(token).isNotBlank().hasSizeLessThanOrEqualTo(256);
        assertThat(refreshCookieAttributes(result)).containsExactlyInAnyOrder("Path=/api/auth",
                "Max-Age=" + REFRESH_MAX_AGE, "Secure", "HttpOnly", "SameSite=Strict");
        assertThat(body(result).has("refreshToken")).isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
        // Solo el hash llega a la base (HU-002 CA-05), y el valor no llega al log.
        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class,
                userId)).isEqualTo(RefreshTokenHasher.sha256Hex(token));
        assertThat(output.getAll()).doesNotContain(token);
    }

    /**
     * El cliente anterior a D36 enviaba el refresh token en el cuerpo. Ahora se ignora: sin cookie
     * es 401, y el token del cuerpo ni siquiera se consume.
     */
    @Test
    void refreshIgnoresATokenSentInTheBody() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String token = loginRefreshToken(email);

        assertRefreshCookieCleared(postJson("/api/auth/refresh", Map.of("refreshToken", token))
                .andExpect(status().isUnauthorized()).andReturn());

        assertThat(jdbc.queryForMap("SELECT used_at, revoked_at FROM refresh_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(token))).allSatisfy((column, value) -> assertThat(value).isNull());
        refresh(token).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ HU-004 logout

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String refreshToken = loginRefreshToken(email);

        // D36: el logout lee la cookie y la manda borrar.
        assertRefreshCookieCleared(logout(refreshToken).andExpect(status().isNoContent()).andReturn());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT revoked_at, revoked_reason FROM refresh_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(refreshToken));
        assertThat(row.get("revoked_at")).isNotNull();
        assertThat(row.get("revoked_reason")).isEqualTo("LOGOUT");

        refresh(refreshToken).andExpect(status().isUnauthorized());

        logout(refreshToken).andExpect(status().isNoContent());
        assertThat(jdbc.queryForMap("SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
                RefreshTokenHasher.sha256Hex(refreshToken)).get("revoked_at")).isEqualTo(row.get("revoked_at"));
        logout("inexistente-" + UUID.randomUUID()).andExpect(status().isNoContent());
        // Sin cookie tambien es 204 y tambien la borra: idempotente, sin confirmar nada.
        assertRefreshCookieCleared(mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent())
                .andReturn());

        assertThat(output.getAll()).doesNotContain(refreshToken);
    }

    @Test
    void logoutRevokesTheWholeFamilyOfTheCookieAfterRotation() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String first = loginRefreshToken(email);
        String current = refreshCookieValue(refresh(first).andExpect(status().isOk()).andReturn());

        logout(current).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("""
                SELECT t.revoked_reason FROM refresh_tokens t JOIN users u ON u.id = t.user_id
                WHERE u.email = ?""", String.class, email)).hasSize(2).containsOnly("LOGOUT");
        refresh(current).andExpect(status().isUnauthorized());
    }

    /** El cliente anterior a D36 mandaba el token en el cuerpo: se ignora y no revoca nada. */
    @Test
    void logoutIgnoresATokenSentInTheBody() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        String token = loginRefreshToken(email);

        postJson("/api/auth/logout", Map.of("refreshToken", token)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?", Object.class,
                RefreshTokenHasher.sha256Hex(token))).isNull();
    }

    @Test
    void logoutWithUnknownTokenAnswersLikeARealLogoutAndTouchesNoRow(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());
        // Una sesion viva: hay al menos una fila que un logout mal hecho podria alterar.
        String liveRefresh = loginRefreshToken(email);
        String unknown = "inexistente-" + UUID.randomUUID();

        List<Map<String, Object>> before = refreshTokensTable();
        MvcResult unknownLogout = logout(unknown)
                .andExpect(status().isNoContent())
                .andReturn();
        List<Map<String, Object>> after = refreshTokensTable();

        // HU-004 CA-04: ni una fila creada ni una columna modificada en toda la tabla.
        assertThat(after).isEqualTo(before);

        // Mismo codigo y mismo mensaje (ninguno: 204 sin cuerpo) que un logout correcto.
        MvcResult realLogout = logout(liveRefresh)
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(unknownLogout.getResponse().getStatus()).isEqualTo(realLogout.getResponse().getStatus());
        assertThat(unknownLogout.getResponse().getContentAsString())
                .isEqualTo(realLogout.getResponse().getContentAsString())
                .isEmpty();
        // D36: la cabecera que borra la cookie tampoco distingue un caso del otro.
        assertThat(refreshCookieAttributes(unknownLogout)).isEqualTo(refreshCookieAttributes(realLogout));

        // HU-004 CA-06: ninguno de los dos valores llega al log.
        assertThat(output.getAll()).doesNotContain(unknown).doesNotContain(liveRefresh);
    }

    // ------------------------------------------------------------------ CORS

    @Test
    void corsPreflightAllowsFrontendOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                // D36: sin esto el navegador no envia ni guarda la cookie del refresh token.
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    /** Una peticion real con credenciales desde el frontend recibe permiso y la cookie. */
    @Test
    void credentialedLoginFromTheFrontendOriginGetsCorsHeadersAndTheCookie() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueDocument());

        MvcResult result = mvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andReturn();
        assertThat(refreshCookieValue(result)).isNotBlank();
    }

    // ------------------------------------------------------------------ reloj controlable

    /**
     * Reloj inyectado en la aplicacion. Sin intervencion devuelve la hora del sistema, de modo que
     * las pruebas que no lo tocan se comportan como en produccion; las que necesitan instantes
     * exactos lo congelan y lo adelantan a mano.
     */
    static final class AdjustableClock extends Clock {

        private volatile Instant frozen;

        void freezeAt(Instant instant) {
            frozen = instant;
        }

        void advance(Duration duration) {
            Instant current = frozen;
            if (current == null) {
                throw new IllegalStateException("Congele el reloj antes de adelantarlo");
            }
            frozen = current.plus(duration);
        }

        void reset() {
            frozen = null;
        }

        @Override
        public Instant instant() {
            Instant current = frozen;
            return current != null ? current : Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
