package com.fcv.citas.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de los JWT de acceso y refresco. Los secretos llegan SIEMPRE por variables de
 * entorno ({@code JWT_ACCESS_SECRET}, {@code JWT_REFRESH_SECRET}) y nunca se registran en log.
 *
 * <p>Este componente solo declara el contrato de configuracion; la emision, validacion y
 * rotacion de tokens las implementa el slice de autenticacion posterior.</p>
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        Duration accessTtl,
        Duration refreshTtl) {

    @Override
    public String toString() {
        // Evita que un log accidental de la configuracion exponga los secretos (PRD, seccion 8).
        return "JwtProperties[accessSecret=***, refreshSecret=***, accessTtl=%s, refreshTtl=%s]"
                .formatted(accessTtl, refreshTtl);
    }
}
