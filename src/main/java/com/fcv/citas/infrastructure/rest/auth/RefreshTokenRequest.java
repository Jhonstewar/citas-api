package com.fcv.citas.infrastructure.rest.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/auth/refresh} y {@code /api/auth/logout}. Nunca en la URL. */
public record RefreshTokenRequest(@NotBlank @Size(max = 256) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=***]";
    }
}
