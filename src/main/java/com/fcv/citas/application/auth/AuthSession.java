package com.fcv.citas.application.auth;

/** Par de tokens entregado al cliente. {@code toString} no expone valores. */
public record AuthSession(String accessToken, String refreshToken, long expiresInSeconds) {

    @Override
    public String toString() {
        return "AuthSession[accessToken=***, refreshToken=***, expiresInSeconds=%d]".formatted(expiresInSeconds);
    }
}
