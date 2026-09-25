package com.fcv.citas.infrastructure.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/** D36: atributos exactos de la cookie {@code fcv_refresh}, con y sin {@code Secure}. */
class RefreshTokenCookiesTest {

    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    @Test
    void issuedCookieHasTheExactContractAttributes() {
        ResponseCookie cookie = new RefreshTokenCookies(true, SEVEN_DAYS).issue("valor-opaco");

        assertThat(cookie.getName()).isEqualTo("fcv_refresh");
        assertThat(cookie.getValue()).isEqualTo("valor-opaco");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isEqualTo(SEVEN_DAYS);
        // La cabecera tal como sale al navegador. Expires depende del reloj.
        assertThat(cookie.toString()).matches("fcv_refresh=valor-opaco; Path=/api/auth; Max-Age=604800; "
                + "Expires=[^;]+; Secure; HttpOnly; SameSite=Strict");
    }

    @Test
    void secureFollowsRefreshCookieSecure() {
        ResponseCookie cookie = new RefreshTokenCookies(false, SEVEN_DAYS).issue("valor-opaco");

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.toString()).doesNotContain("Secure").contains("HttpOnly").contains("SameSite=Strict");
    }

    @Test
    void clearedCookieIsEmptyWithMaxAgeZeroAndTheSameScope() {
        ResponseCookie cookie = new RefreshTokenCookies(true, SEVEN_DAYS).clear();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        // Mismo Path: si no, el navegador consideraria que es otra cookie y no borraria la real.
        assertThat(cookie.toString()).startsWith("fcv_refresh=; Path=/api/auth; Max-Age=0; ")
                .endsWith("; Secure; HttpOnly; SameSite=Strict");
    }

    @Test
    void implausibleValuesAreRejectedWithoutTouchingTheDatabase() {
        assertThat(RefreshTokenCookies.isPlausible(null)).isFalse();
        assertThat(RefreshTokenCookies.isPlausible("")).isFalse();
        assertThat(RefreshTokenCookies.isPlausible("  ")).isFalse();
        assertThat(RefreshTokenCookies.isPlausible("x".repeat(257))).isFalse();
        assertThat(RefreshTokenCookies.isPlausible("x".repeat(256))).isTrue();
    }
}
