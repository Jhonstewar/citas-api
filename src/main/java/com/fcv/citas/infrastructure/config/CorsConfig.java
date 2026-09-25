package com.fcv.citas.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS explicito para el frontend {@code citas-web} (React contra REST directo, sin BFF).
 *
 * <p>La lista de origenes llega por variable de entorno ({@code FRONTEND_ORIGIN}). Desde D36 se
 * admiten credenciales ({@code Access-Control-Allow-Credentials: true}): el navegador solo envia
 * y guarda la cookie {@code fcv_refresh} en peticiones entre origenes si CORS lo permite. Con
 * credenciales, un comodin equivaldria a entregar la sesion a cualquier sitio, asi que la
 * aplicacion NO arranca si la lista contiene {@code *} o un patron.</p>
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        return corsConfigurationSource(properties.allowedOrigins());
    }

    static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        requireExactOrigins(allowedOrigins);
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins.stream().map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void requireExactOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("FRONTEND_ORIGIN no define ningun origen permitido para CORS");
        }
        for (String origin : allowedOrigins) {
            if (origin == null || origin.isBlank() || origin.contains("*")) {
                throw new IllegalStateException("FRONTEND_ORIGIN debe listar origenes exactos (sin '*'): la API "
                        + "admite credenciales y un comodin expondria la sesion a cualquier sitio");
            }
        }
    }
}
