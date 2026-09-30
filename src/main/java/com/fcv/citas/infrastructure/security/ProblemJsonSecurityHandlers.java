package com.fcv.citas.infrastructure.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 401 y 403 de la cadena de seguridad con el mismo formato ProblemDetail que el
 * {@code @RestControllerAdvice}. Nunca se incluye el token recibido.
 */
@Component
class ProblemJsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ProblemJsonSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // RFC 6750: 401 con WWW-Authenticate: Bearer. El BearerTokenAuthenticationEntryPoint de Spring
        // pone error_description en ingles y con detalle del decodificador JWT; aqui se compone a mano
        // con un texto fijo en español y solo el codigo de error estandar.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate(authException));
        write(response, HttpStatus.UNAUTHORIZED, "No autenticado",
                "Se requiere un access token válido", request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tiene permisos para realizar esta operación", request);
    }

    static String wwwAuthenticate(AuthenticationException authException) {
        if (authException instanceof OAuth2AuthenticationException oauth2) {
            String code = oauth2.getError().getErrorCode();
            if (OAuth2ErrorCodes.INVALID_TOKEN.equals(code)) {
                return "Bearer error=\"invalid_token\", error_description=\"El access token es inválido o ha expirado\"";
            }
            if (OAuth2ErrorCodes.INVALID_REQUEST.equals(code)) {
                return "Bearer error=\"invalid_request\", error_description=\"La petición de autenticación no es válida\"";
            }
        }
        return "Bearer";
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail,
            HttpServletRequest request) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
