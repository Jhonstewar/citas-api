package com.fcv.citas.infrastructure.rest.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.fcv.citas.infrastructure.security.JwtProperties;
import com.fcv.citas.infrastructure.security.RefreshCookieProperties;

/**
 * Cookie {@code fcv_refresh} que transporta el refresh token opaco (D36). JavaScript no la puede
 * leer ({@code HttpOnly}) y sobrevive a la recarga de la pagina, que era el motivo del cambio.
 *
 * <ul>
 *   <li>{@code HttpOnly}: un XSS no puede leer el token;</li>
 *   <li>{@code Secure} segun {@code REFRESH_COOKIE_SECURE} (por defecto si);</li>
 *   <li>{@code SameSite=Strict}: nunca viaja en una peticion iniciada desde otro sitio (CSRF);</li>
 *   <li>{@code Path=/api/auth}: solo llega a las rutas de sesion, no al resto de la API;</li>
 *   <li>{@code Max-Age} = vida del refresh token ({@code JWT_REFRESH_DAYS}) en segundos.</li>
 * </ul>
 *
 * <p>El valor de la cookie nunca se registra: {@link ResponseCookie#toString()} solo se usa para
 * escribir la cabecera {@code Set-Cookie}.</p>
 */
@Component
class RefreshTokenCookies {

    static final String NAME = "fcv_refresh";
    static final String PATH = "/api/auth";

    /** Mismo tope que tenia el campo {@code refreshToken} del cuerpo antes de D36. */
    static final int MAX_TOKEN_LENGTH = 256;

    private final boolean secure;
    private final Duration maxAge;

    @Autowired
    RefreshTokenCookies(RefreshCookieProperties cookieProperties, JwtProperties jwtProperties) {
        this(cookieProperties.secure(), jwtProperties.refreshTtl());
    }

    RefreshTokenCookies(boolean secure, Duration maxAge) {
        this.secure = secure;
        this.maxAge = maxAge;
    }

    /** Cookie con el refresh token recien emitido o rotado. */
    ResponseCookie issue(String refreshToken) {
        return base(refreshToken).maxAge(maxAge).build();
    }

    /** Cookie que el navegador borra de inmediato ({@code Max-Age=0}). */
    ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    /** Descarta sin consultar la base lo que no puede ser un refresh token. */
    static boolean isPlausible(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TOKEN_LENGTH;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
