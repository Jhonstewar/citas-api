package com.fcv.citas.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Extrae el access token de la cabecera {@code Authorization} en toda la API salvo en las rutas
 * publicas de autenticacion, donde la ignora.
 *
 * <p>Por que: el filtro del resource server valida cualquier {@code Bearer} que reciba ANTES de
 * que la regla {@code permitAll} llegue a evaluarse. Un cliente que conserva un access token
 * caducado y llama a {@code /api/auth/refresh} —justo cuando mas necesita renovar— o a
 * {@code /api/auth/logout} recibia un 401 y la peticion no se procesaba: la renovacion fallaba y
 * el logout no revocaba nada. Esas rutas se autentican con el CUERPO (credenciales o refresh
 * token), nunca con el access token, asi que la cabecera no aporta nada y solo estorba.</p>
 *
 * <p>Fuera de esas rutas se delega sin cambios en {@link DefaultBearerTokenResolver}: el resto de
 * la API sigue exigiendo un access token valido exactamente igual que antes.</p>
 */
final class PublicEndpointsBearerTokenResolver implements BearerTokenResolver {

    private final RequestMatcher publicEndpoints;
    private final BearerTokenResolver delegate;

    PublicEndpointsBearerTokenResolver(RequestMatcher publicEndpoints) {
        this(publicEndpoints, new DefaultBearerTokenResolver());
    }

    PublicEndpointsBearerTokenResolver(RequestMatcher publicEndpoints, BearerTokenResolver delegate) {
        this.publicEndpoints = publicEndpoints;
        this.delegate = delegate;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        // Devolver null equivale a "no llego token": la peticion sigue como anonima y la decide
        // la regla de autorizacion de la ruta, que para estas es permitAll.
        if (publicEndpoints.matches(request)) {
            return null;
        }
        return delegate.resolve(request);
    }
}
