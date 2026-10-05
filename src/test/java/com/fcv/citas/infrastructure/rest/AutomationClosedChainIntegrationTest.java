package com.fcv.citas.infrastructure.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.TestTokens;

/**
 * HU-034 CA-09 (parte vacia): con {@code AUTOMATION_API_KEY} vacia la API arranca, pero la cadena de
 * automatizacion rechaza todo con 401, incluso una cabecera con cualquier valor (nada coincide con "").
 * Contexto propio: sobrescribe la clave de {@code application-test.yml}.
 */
@SpringBootTest(properties = "app.automation.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutomationClosedChainIntegrationTest {

    private static final String URL = "/api/automation/appointments/upcoming";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void emptyConfiguredKeyRejectsEverythingWith401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("X-Automation-Key", "")).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("X-Automation-Key", "cualquier-valor")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
        mvc.perform(get(URL).header("X-Automation-Key", "test-only-automation-key-0123456789abcdef"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyConfiguredKeyDoesNotLetAPersonJwtThrough() throws Exception {
        String bearer = new TestTokens(jwtEncoder).bearer(1L, Role.ADMIN);
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isUnauthorized());
    }
}
