package com.fcv.citas.infrastructure.rest.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.auth.AuthSession;
import com.fcv.citas.application.auth.LoginUseCase;
import com.fcv.citas.application.auth.LogoutUseCase;
import com.fcv.citas.application.auth.RefreshSessionUseCase;
import com.fcv.citas.application.auth.RegisterUserCommand;
import com.fcv.citas.application.auth.RegisterUserUseCase;
import com.fcv.citas.domain.auth.InvalidRefreshTokenException;

/**
 * Adaptador REST de HU-001..HU-004. Sin reglas de negocio: solo traduce HTTP a casos de uso.
 *
 * <p>D36: el refresh token entra y sale SOLO por la cookie {@code HttpOnly} {@code fcv_refresh}.
 * {@code refresh} y {@code logout} no tienen cuerpo; si el cliente envia uno, se ignora. La
 * rotacion y la deteccion de reuso por familia siguen en {@link RefreshSessionUseCase}.</p>
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final RegisterUserUseCase registerUser;
    private final LoginUseCase login;
    private final RefreshSessionUseCase refreshSession;
    private final LogoutUseCase logout;
    private final RefreshTokenCookies cookies;

    AuthController(RegisterUserUseCase registerUser, LoginUseCase login, RefreshSessionUseCase refreshSession,
            LogoutUseCase logout, RefreshTokenCookies cookies) {
        this.registerUser = registerUser;
        this.login = login;
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(registerUser.register(new RegisterUserCommand(request.firstNames(),
                request.lastNames(), request.documentType(), request.documentNumber(), request.email(),
                request.phone(), request.password(), request.insurancePlanId())));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(login.login(request.email(), request.password()));
    }

    /**
     * Renueva con la cookie. Ausente, inverosimil, inexistente, caducada, revocada o reusada: el
     * mismo 401 de siempre y la cookie borrada, para que el navegador no la siga presentando.
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (!RefreshTokenCookies.isPlausible(refreshToken)) {
            throw rejected(response, new InvalidRefreshTokenException());
        }
        try {
            return withRefreshCookie(refreshSession.refresh(refreshToken));
        } catch (InvalidRefreshTokenException e) {
            throw rejected(response, e);
        }
    }

    /** Revoca la familia de la cookie, si la hay, y la borra. Idempotente: 204 siempre. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        if (RefreshTokenCookies.isPlausible(refreshToken)) {
            logout.logout(refreshToken);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken()).toString())
                .body(TokenResponse.from(session));
    }

    /**
     * La cabecera se escribe en la respuesta ANTES de propagar la excepcion: el manejador global
     * compone el cuerpo 401 sobre esta misma respuesta y conserva sus cabeceras.
     */
    private InvalidRefreshTokenException rejected(HttpServletResponse response, InvalidRefreshTokenException e) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        return e;
    }
}
