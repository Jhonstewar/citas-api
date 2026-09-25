package com.fcv.citas.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cookie {@code fcv_refresh} que transporta el refresh token (D36).
 *
 * @param secure atributo {@code Secure}; {@code REFRESH_COOKIE_SECURE}, por defecto {@code true}.
 *               Los navegadores aceptan cookies {@code Secure} en {@code http://localhost}, asi que
 *               el laboratorio funciona sin HTTPS; apagarlo solo tiene sentido en un host HTTP que
 *               no sea {@code localhost}.
 */
@ConfigurationProperties(prefix = "app.security.refresh-cookie")
public record RefreshCookieProperties(@DefaultValue("true") boolean secure) {
}
