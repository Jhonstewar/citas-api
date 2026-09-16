package com.fcv.citas.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origenes permitidos para CORS. El PRD exige CORS explicito: no se usa comodin.
 *
 * @param allowedOrigins origenes exactos del frontend, tomados de {@code FRONTEND_ORIGIN}.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
