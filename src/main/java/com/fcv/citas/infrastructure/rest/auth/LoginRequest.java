package com.fcv.citas.infrastructure.rest.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/auth/login}. */
public record LoginRequest(
        @NotBlank @Size(max = 160) String email,
        @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[password=***]";
    }
}
