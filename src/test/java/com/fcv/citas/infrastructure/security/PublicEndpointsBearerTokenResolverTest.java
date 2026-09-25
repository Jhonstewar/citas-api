package com.fcv.citas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Fija el alcance exacto de la excepcion: SOLO {@code POST} sobre las seis rutas publicas de
 * autenticacion ignora la cabecera {@code Authorization}. Cualquier otra combinacion de metodo y
 * ruta sigue leyendo el token, y por tanto sigue exigiendo que sea valido.
 */
class PublicEndpointsBearerTokenResolverTest {

    private static final String TOKEN = "cabecera.del.token";

    private final BearerTokenResolver resolver =
            new PublicEndpointsBearerTokenResolver(SecurityConfig.PUBLIC_AUTH_ENDPOINTS);

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
        "/api/auth/password-recovery", "/api/auth/password-reset" })
    void ignoresTheAuthorizationHeaderOnPublicAuthPosts(String path) {
        assertThat(resolver.resolve(request("POST", path))).isNull();
    }

    @Test
    void resolvesTheTokenOnProtectedRoutes() {
        assertThat(resolver.resolve(request("GET", "/api/me"))).isEqualTo(TOKEN);
    }

    @Test
    void onlyPostIsExempt() {
        // Otro metodo sobre la misma ruta no es publico: el token se sigue leyendo.
        assertThat(resolver.resolve(request("GET", "/api/auth/login"))).isEqualTo(TOKEN);
        assertThat(resolver.resolve(request("DELETE", "/api/auth/logout"))).isEqualTo(TOKEN);
    }

    @Test
    void doesNotMatchByPrefixOrSuffix() {
        // Una ruta futura que empiece igual no hereda la excepcion por accidente.
        assertThat(resolver.resolve(request("POST", "/api/auth/login/extra"))).isEqualTo(TOKEN);
        assertThat(resolver.resolve(request("POST", "/api/auth/logout-all"))).isEqualTo(TOKEN);
        assertThat(resolver.resolve(request("POST", "/api/auth"))).isEqualTo(TOKEN);
    }
}
