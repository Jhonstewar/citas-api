package com.fcv.citas.infrastructure.rest.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/auth/login}.
 *
 * <p>{@code password} no lleva tope de longitud a proposito: una contraseña de mas de 72 bytes no
 * es una peticion mal formada, es una credencial que no coincide con ninguna cuenta. Debe recibir
 * el mismo 401 {@code Credenciales inválidas} que cualquier otra, no un 400 que describa la regla
 * (lo resuelve {@code SpringPasswordHasher}).</p>
 */
public record LoginRequest(
        @NotBlank @Size(max = 160) String email,
        @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginRequest[password=***]";
    }
}
