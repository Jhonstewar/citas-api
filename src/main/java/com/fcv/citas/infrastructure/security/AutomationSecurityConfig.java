package com.fcv.citas.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Segunda cadena de seguridad (HU-034): solo {@code /api/automation/**}, autenticada por clave de API
 * y no por JWT. Va ANTES de la principal ({@link SecurityConfig}, que no declara matcher y por tanto
 * captura todo lo demas, con su {@code denyAll} intacto).
 *
 * <p>No hay {@code oauth2ResourceServer} en esta cadena: un JWT de persona ni se lee ni sustituye
 * la clave. Cualquier rol de persona queda fuera porque la unica autoridad aceptada es la que
 * concede {@link AutomationApiKeyFilter}.</p>
 */
@Configuration
class AutomationSecurityConfig {

    static final String AUTOMATION_PATH = "/api/automation/**";

    @Bean
    @Order(1)
    SecurityFilterChain automationSecurityFilterChain(HttpSecurity http, AutomationProperties properties,
            ProblemJsonSecurityHandlers problemHandlers) throws Exception {
        return http
                .securityMatcher(AUTOMATION_PATH)
                // CSRF deshabilitado de forma consciente, como en la cadena principal: API stateless, sin
                // cookies ni sesion en esta cadena; la credencial viaja solo en una cabecera propia
                // que un sitio ajeno no puede fijar.
                .csrf(csrf -> csrf.disable())
                // Sin CORS: el cliente es n8n (servidor a servidor), no un navegador.
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new AutomationApiKeyFilter(properties, problemHandlers), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("AUTOMATION"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                problemHandlers.commenceApiKey(request, response))
                        .accessDeniedHandler(problemHandlers))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }
}
