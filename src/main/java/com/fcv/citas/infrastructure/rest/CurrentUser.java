package com.fcv.citas.infrastructure.rest;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Identidad del usuario autenticado, en UN solo sitio (HU-005, R3 de S4). El titular de toda
 * operacion sale del {@code sub} del token, nunca de un campo del cuerpo (HU-017 CA-06, HU-023 CA-09,
 * HU-024 CA-09). Con ese id, la politica {@code application.shared.Ownership} decide si un recurso
 * es suyo.
 */
public final class CurrentUser {

    private static final String ROLE_PREFIX = "ROLE_";

    private CurrentUser() {
    }

    public static long id(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getToken().getSubject());
    }

    /**
     * Roles de la sesion tomados del contexto de seguridad (authorities {@code ROLE_*} convertidas del
     * claim {@code roles}), sin el prefijo.
     */
    public static List<String> roles(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .toList();
    }
}
