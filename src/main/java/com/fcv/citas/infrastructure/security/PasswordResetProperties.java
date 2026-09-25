package com.fcv.citas.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Recuperacion de contraseña (HU-006/HU-007, D27).
 *
 * @param ttl         vigencia del token; {@code PASSWORD_RESET_MINUTES}, por defecto 30 minutos.
 * @param exposeToken {@code PASSWORD_RESET_EXPOSE_TOKEN}, por defecto {@code false}. Solo para el
 *                    laboratorio: con {@code true}, la respuesta de un email EXISTENTE lleva
 *                    {@code devToken} y deja de ser identica a la de un email inexistente. Fuera
 *                    del laboratorio debe quedarse apagada.
 */
@ConfigurationProperties(prefix = "app.security.password-reset")
public record PasswordResetProperties(
        @DefaultValue("30m") Duration ttl,
        @DefaultValue("false") boolean exposeToken) {
}
