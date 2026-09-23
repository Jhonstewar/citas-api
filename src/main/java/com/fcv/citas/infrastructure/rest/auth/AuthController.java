package com.fcv.citas.infrastructure.rest.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.auth.LoginUseCase;
import com.fcv.citas.application.auth.LogoutUseCase;
import com.fcv.citas.application.auth.RefreshSessionUseCase;
import com.fcv.citas.application.auth.RegisterUserCommand;
import com.fcv.citas.application.auth.RegisterUserUseCase;

/** Adaptador REST de HU-001..HU-004. Sin reglas de negocio: solo traduce HTTP a casos de uso. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final RegisterUserUseCase registerUser;
    private final LoginUseCase login;
    private final RefreshSessionUseCase refreshSession;
    private final LogoutUseCase logout;

    AuthController(RegisterUserUseCase registerUser, LoginUseCase login, RefreshSessionUseCase refreshSession,
            LogoutUseCase logout) {
        this.registerUser = registerUser;
        this.login = login;
        this.refreshSession = refreshSession;
        this.logout = logout;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(registerUser.register(new RegisterUserCommand(request.firstNames(),
                request.lastNames(), request.documentType(), request.documentNumber(), request.email(),
                request.phone(), request.password(), request.insurancePlanId())));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(login.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(refreshSession.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshTokenRequest request) {
        logout.logout(request.refreshToken());
    }
}
