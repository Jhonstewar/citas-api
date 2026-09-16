package com.fcv.citas.domain.auth;

import java.time.Instant;

/** Access token emitido. {@code toString} no expone el valor. */
public record IssuedAccessToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedAccessToken[value=***, expiresAt=%s]".formatted(expiresAt);
    }
}
