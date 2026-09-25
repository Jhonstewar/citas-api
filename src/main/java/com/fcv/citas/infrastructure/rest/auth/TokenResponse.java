package com.fcv.citas.infrastructure.rest.auth;

import com.fcv.citas.application.auth.AuthSession;

/**
 * Respuesta de login y refresh. {@code expiresIn} en segundos (vida del access token).
 *
 * <p>Desde D36 NO lleva el refresh token: viaja solo en la cookie {@code HttpOnly}
 * {@code fcv_refresh}, fuera del alcance de JavaScript.</p>
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    static TokenResponse from(AuthSession session) {
        return new TokenResponse(session.accessToken(), "Bearer", session.expiresInSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, expiresIn=%d]".formatted(expiresIn);
    }
}
