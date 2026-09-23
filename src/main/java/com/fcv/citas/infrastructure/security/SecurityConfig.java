package com.fcv.citas.infrastructure.security;

import java.util.Arrays;

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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
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

    /**
     * Un unico matcher para las rutas publicas de autenticacion, compartido por la regla
     * {@code permitAll} y por {@link PublicEndpointsBearerTokenResolver}. Si cada uno tuviera su
     * propia lista podrian divergir: una ruta publica que aun rechaza un Bearer caducado, o una
     * ruta protegida que deja de leer el token.
     */
    static final RequestMatcher PUBLIC_AUTH_ENDPOINTS = new OrRequestMatcher(Arrays.stream(PUBLIC_AUTH_POST)
            .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path))
            .toList());

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
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers("/error").permitAll()
                        // HU-009: unica lectura de catalogo publica. La consume el formulario de
                        // registro, que por definicion no tiene sesion todavia, asi que exigir
                        // token la haria inutilizable. Se declara SIN metodo, como el resto de
                        // catalogos, para que un POST llegue a MVC y responda 405 y no 401
                        // (HU-010 CA-06). No expone nada sensible: son planes comerciales de EPS.
                        .requestMatchers("/api/catalogs/insurance-plans").permitAll()
                        // HU-005: un prefijo por rol. Los roles vienen del claim `roles` del token.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/professional/**").hasRole("PROFESSIONAL")
                        .requestMatchers("/api/patient/**").hasRole("USER")
                        // Lecturas comunes a cualquier rol. Todos los metodos para que una escritura
                        // sobre un catalogo fijo llegue a MVC y responda 405 (HU-010 CA-06).
                        .requestMatchers("/api/catalogs/**", "/api/me").authenticated()
                        // HU-005 CA-07: denegacion por defecto de toda ruta no declarada.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Las rutas publicas de auth ignoran la cabecera Authorization: un Bearer
                        // caducado no puede impedir renovar ni cerrar sesion.
                        .bearerTokenResolver(new PublicEndpointsBearerTokenResolver(PUBLIC_AUTH_ENDPOINTS))
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
