package com.fcv.citas.infrastructure.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Cadena de seguridad stateless: resource server JWT (HS256), CORS explicito, endpoints de
 * autenticacion publicos y el resto de la API protegida.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** Rutas publicas del slice de autenticacion (HU-001..HU-004). */
    static final String[] PUBLIC_AUTH_POST = {
        "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            // Se cualifica por nombre: MVC registra tambien mvcHandlerMappingIntrospector
            // como CorsConfigurationSource y el tipo por si solo es ambiguo.
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ProblemJsonSecurityHandlers problemHandlers)
            throws Exception {
        return http
                // Valido solo mientras el token viaje en la cabecera Authorization (nunca en cookie).
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POST).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    /**
     * Hash adaptativo exigido por el PRD (seccion 8): BCrypt por defecto, con prefijo
     * {@code {bcrypt}} que permite migrar de algoritmo sin romper hashes existentes.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
