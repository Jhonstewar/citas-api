package com.fcv.citas.infrastructure.rest.auth;

import com.fcv.citas.application.auth.AuthSession;

/** Respuesta de login y refresh. {@code expiresIn} en segundos (vida del access token). */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    static TokenResponse from(AuthSession session) {
        return new TokenResponse(session.accessToken(), session.refreshToken(), "Bearer",
                session.expiresInSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, refreshToken=***, expiresIn=%d]".formatted(expiresIn);
    }
}
