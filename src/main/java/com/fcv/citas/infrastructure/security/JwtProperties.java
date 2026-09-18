package com.fcv.citas.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros del JWT de acceso y de la vigencia del refresh token. El secreto llega SIEMPRE por
 * variable de entorno ({@code JWT_ACCESS_SECRET}) y nunca se registra en log.
 *
 * <p>No hay secreto de refresco: por DEC-002 el refresh token es OPACO —un valor aleatorio del
 * que solo se persiste el hash SHA-256—, no un JWT firmado, asi que no habia nada que firmar con
 * el. La propiedad existia sin usarse e invitaba a creer lo contrario.</p>
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String accessSecret,
        Duration accessTtl,
        Duration refreshTtl) {

    @Override
    public String toString() {
        // Evita que un log accidental de la configuracion exponga el secreto (PRD, seccion 8).
        return "JwtProperties[accessSecret=***, accessTtl=%s, refreshTtl=%s]"
                .formatted(accessTtl, refreshTtl);
    }
}
