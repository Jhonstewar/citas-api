package com.fcv.citas.infrastructure.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.TestTokens;

/**
 * HU-005 (verificacion 6 de S3): autorizacion por rol en los prefijos de la API.
 * Anonimo → 401, rol equivocado → 403, rol correcto → pasa el filtro. Denegacion por defecto.
 *
 * <p>Solo comprueba el filtro de seguridad: los ids de usuario de los tokens no existen en la base,
 * y las rutas elegidas no necesitan que existan (listados y catalogos).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    private static final long SOME_USER = 900_001L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtEncoder jwtEncoder;

    private TestTokens tokens;

    @BeforeEach
    void setUp() {
        tokens = new TestTokens(jwtEncoder);
    }

    // ------------------------------------------------------------------ CA-01: 401 distinto de 403

    @Test
    void anonymousGets401AndWrongRoleGets403OnAdminRoutes() throws Exception {
        mvc.perform(get("/api/admin/specialties")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/specialties")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    // ------------------------------------------------------------------ CA-02..CA-04: cada prefijo, su rol

    @Test
    void onlyAdminReachesAdminRoutes() throws Exception {
        mvc.perform(get("/api/admin/specialties")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.PROFESSIONAL)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/specialties")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyProfessionalReachesProfessionalRoutes() throws Exception {
        mvc.perform(get("/api/professional/blocks?from=2030-01-01&to=2030-01-07")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/professional/blocks?from=2030-01-01&to=2030-01-07")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyUserReachesPatientRoutes() throws Exception {
        mvc.perform(get("/api/patient/appointments")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.PROFESSIONAL)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patient/appointments")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patient/appointments")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.USER)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ CA-07: denegacion por defecto

    @Test
    void undeclaredRoutesAreDeniedEvenWhenAuthenticated() throws Exception {
        mvc.perform(get("/api/undeclared/resource")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.ADMIN)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ HU-010: catalogos

    @ParameterizedTest
    @ValueSource(strings = { "USER", "PROFESSIONAL", "ADMIN" })
    void anyAuthenticatedRoleReadsCatalogs(String role) throws Exception {
        mvc.perform(get("/api/catalogs/sites")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(SOME_USER, Role.valueOf(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void catalogsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/catalogs/sites")).andExpect(status().isUnauthorized());
    }

    /** HU-010 CA-06: los catalogos fijos no tienen escritura. */
    @Test
    void fixedCatalogsRejectWrites() throws Exception {
        String admin = tokens.bearer(SOME_USER, Role.ADMIN);
        mvc.perform(post("/api/catalogs/sites").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/catalogs/appointment-statuses").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isMethodNotAllowed());
    }
}
