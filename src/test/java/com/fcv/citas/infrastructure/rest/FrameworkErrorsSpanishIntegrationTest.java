package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * F9 (S4): los errores que genera el framework (404, 405, 415) y la cabecera
 * {@code WWW-Authenticate} salen en español, con los mismos codigos HTTP de siempre y sin ecos del
 * valor recibido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FrameworkErrorsSpanishIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void unknownRouteForAuthenticatedUserIs404InSpanish() throws Exception {
        mvc.perform(get("/api/me/no-existe").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("problem+json")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail").value("El recurso solicitado no existe"));
    }

    @Test
    void wrongMethodIs405InSpanishAndKeepsAllowHeader() throws Exception {
        mvc.perform(post("/api/me").with(jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists(HttpHeaders.ALLOW))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Método no permitido"))
                .andExpect(jsonPath("$.detail").value("El método HTTP no está permitido para este recurso"));
    }

    @Test
    void unsupportedContentTypeIs415InSpanish() throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.startsWith("El tipo de contenido de la petición no es compatible")))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("Content-Type").doesNotContain("not supported");
    }

    @Test
    void unsupportedContentTypeOnAuthenticatedPutIs415() throws Exception {
        mvc.perform(put("/api/me").with(jwt()).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"));
    }

    @Test
    void missingQueryParameterKeepsSpanish400() throws Exception {
        mvc.perform(get("/api/professional/appointments").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PROFESSIONAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Falta el parámetro obligatorio")))
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void missingTokenChallengeIsPlainBearer() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void invalidTokenChallengeIsSpanishWithoutDecoderDetails() throws Exception {
        String challenge = mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andReturn().getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE);
        assertThat(challenge)
                .startsWith("Bearer error=\"invalid_token\"")
                .contains("El access token es inválido o ha expirado")
                .doesNotContain("Jwt")
                .doesNotContain("decode")
                .doesNotContain("esto-no-es-un-jwt");
    }
}
