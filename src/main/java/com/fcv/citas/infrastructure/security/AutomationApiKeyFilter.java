package com.fcv.citas.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a n8n en {@code /api/automation/**} con la cabecera {@code X-Automation-Key} (HU-034).
 *
 * <ul>
 *   <li>Clave ausente o invalida: 401 con cualquier metodo. Clave valida con un metodo distinto de
 *       {@code GET}: 405 con {@code Allow: GET}, sin llegar al controlador.</li>
 *   <li>Comparacion en tiempo constante: {@link MessageDigest#isEqual} sobre el SHA-256 de ambos
 *       valores, de modo que ni el contenido ni la longitud de la clave se filtran por tiempo.</li>
 *   <li>Con la clave configurada vacia la cadena esta cerrada: todo 401.</li>
 *   <li>No es un {@code @Component}: se instancia en {@link AutomationSecurityConfig} para que Spring
 *       Boot no lo registre ademas como filtro global del contenedor.</li>
 *   <li>Nunca registra la clave ni la cabecera recibida.</li>
 * </ul>
 */
final class AutomationApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Automation-Key";

    private final byte[] expectedDigest;
    private final boolean enabled;
    private final ProblemJsonSecurityHandlers problemHandlers;

    AutomationApiKeyFilter(AutomationProperties properties, ProblemJsonSecurityHandlers problemHandlers) {
        this.enabled = properties.enabled();
        this.expectedDigest = sha256(properties.apiKey());
        this.problemHandlers = problemHandlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!validKey(request.getHeader(HEADER))) {
            SecurityContextHolder.clearContext();
            problemHandlers.commenceApiKey(request, response);
            return;
        }
        // Clave valida pero metodo de escritura: 405 aqui, sin llegar al controlador (HU-034 CA-07).
        if (!HttpMethod.GET.matches(request.getMethod())) {
            SecurityContextHolder.clearContext();
            problemHandlers.methodNotAllowed(request, response);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("automation", null,
                List.of(new SimpleGrantedAuthority("ROLE_AUTOMATION"))));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean validKey(String presented) {
        if (!enabled || presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(sha256(presented), expectedDigest);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
