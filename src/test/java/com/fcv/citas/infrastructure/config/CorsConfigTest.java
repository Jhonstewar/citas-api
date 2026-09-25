package com.fcv.citas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

/**
 * CORS con credenciales (D36). El origen real de este workspace es {@code http://localhost:5174}
 * ({@code FRONTEND_ORIGIN} del {@code .env.example}); las pruebas de integracion usan el 5173 del
 * perfil de pruebas, asi que el 5174 se comprueba aqui con la misma configuracion que usa la API.
 */
class CorsConfigTest {

    private static final String WORKSPACE_ORIGIN = "http://localhost:5174";

    private static MockHttpServletResponse preflight(CorsConfigurationSource source, String origin)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/refresh");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        new DefaultCorsProcessor().processRequest(configuration, request, response);
        return response;
    }

    @Test
    void preflightFromTheWorkspaceFrontendIsAllowedWithCredentials() throws Exception {
        CorsConfigurationSource source = CorsConfig.corsConfigurationSource(List.of(WORKSPACE_ORIGIN));

        MockHttpServletResponse response = preflight(source, WORKSPACE_ORIGIN);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(WORKSPACE_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
    }

    @Test
    void anotherOriginIsRejected() throws Exception {
        CorsConfigurationSource source = CorsConfig.corsConfigurationSource(List.of(WORKSPACE_ORIGIN));

        MockHttpServletResponse response = preflight(source, "http://localhost:5173");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test
    void aWildcardNeverReachesACredentialedConfiguration() {
        assertThatThrownBy(() -> CorsConfig.corsConfigurationSource(List.of("*")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CorsConfig.corsConfigurationSource(List.of(WORKSPACE_ORIGIN, "https://*.example")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CorsConfig.corsConfigurationSource(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
