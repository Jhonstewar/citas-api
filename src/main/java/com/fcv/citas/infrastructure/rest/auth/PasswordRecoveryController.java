package com.fcv.citas.infrastructure.rest.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.auth.RequestPasswordRecoveryUseCase;
import com.fcv.citas.application.auth.ResetPasswordUseCase;
import com.fcv.citas.infrastructure.rest.validation.PasswordPolicyCompliant;

/**
 * HU-006 y HU-007: recuperacion de contraseña. Rutas publicas: quien las usa no puede
 * autenticarse. El token viaja siempre en el CUERPO, nunca en la ruta ni en la consulta.
 */
@RestController
@RequestMapping("/api/auth")
class PasswordRecoveryController {

    /** Mismo texto exista o no el email: no revela que correos estan registrados (HU-006 CA-02). */
    static final String RECOVERY_MESSAGE =
            "Si el correo corresponde a una cuenta, recibirás las instrucciones para restablecer la contraseña";

    record PasswordRecoveryRequest(@NotBlank @Email @Size(max = 160) String email) {

        @Override
        public String toString() {
            return "PasswordRecoveryRequest[email=***]";
        }
    }

    /** {@code devToken} solo con {@code PASSWORD_RESET_EXPOSE_TOKEN=true} (D27); nulo se omite. */
    record PasswordRecoveryResponse(String message, String devToken) {

        @Override
        public String toString() {
            return "PasswordRecoveryResponse[devToken=%s]".formatted(devToken == null ? "none" : "***");
        }
    }

    record PasswordResetRequest(
            @NotBlank @Size(max = 256) String token,
            @NotBlank @PasswordPolicyCompliant String newPassword) {

        @Override
        public String toString() {
            return "PasswordResetRequest[token=***, newPassword=***]";
        }
    }

    private final RequestPasswordRecoveryUseCase requestRecovery;
    private final ResetPasswordUseCase resetPassword;

    PasswordRecoveryController(RequestPasswordRecoveryUseCase requestRecovery, ResetPasswordUseCase resetPassword) {
        this.requestRecovery = requestRecovery;
        this.resetPassword = resetPassword;
    }

    @PostMapping("/password-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PasswordRecoveryResponse requestRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        RequestPasswordRecoveryUseCase.Result result = requestRecovery.request(request.email());
        return new PasswordRecoveryResponse(RECOVERY_MESSAGE, result.devToken().orElse(null));
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody PasswordResetRequest request) {
        resetPassword.reset(request.token(), request.newPassword());
    }
}
