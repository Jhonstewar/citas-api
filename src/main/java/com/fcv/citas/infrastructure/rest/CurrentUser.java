package com.fcv.citas.infrastructure.rest;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Identidad del usuario autenticado. El titular de toda operacion sale del {@code sub} del token,
 * nunca de un campo del cuerpo (HU-017 CA-06, HU-023 CA-09, HU-024 CA-09).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(JwtAuthenticationToken authentication) {
        return Long.parseLong(authentication.getToken().getSubject());
    }
}
