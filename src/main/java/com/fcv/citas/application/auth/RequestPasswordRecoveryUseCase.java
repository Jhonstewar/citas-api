package com.fcv.citas.application.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.PasswordResetNotifier;
import com.fcv.citas.domain.auth.PasswordResetToken;
import com.fcv.citas.domain.auth.PasswordResetTokenRepository;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.fcv.citas.domain.auth.SecureTokenGenerator;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/**
 * HU-006: solicitar la recuperacion de la contraseña por email.
 *
 * <p>El resultado observable es el MISMO exista o no el email (PRD §8): si no existe, o la cuenta
 * esta inactiva, no se crea nada y se devuelve un resultado vacio, igual que con la exposicion
 * de laboratorio apagada. Si existe, se revocan los tokens anteriores sin usar del usuario, se
 * emite uno nuevo del que solo se guarda el SHA-256 y se entrega por el puerto de notificacion.</p>
 *
 * <p>D27: el token en claro solo sale de aqui si {@code exposeTokenInResponse} esta activo —una
 * variable de laboratorio, apagada por defecto—. Nunca se registra en el log.</p>
 */
public class RequestPasswordRecoveryUseCase {

    /** Resultado. {@code devToken} solo tiene valor con la exposicion de laboratorio activa. */
    public record Result(Optional<String> devToken) {

        static final Result EMPTY = new Result(Optional.empty());

        @Override
        public String toString() {
            return "Result[devToken=%s]".formatted(devToken.isPresent() ? "***" : "none");
        }
    }

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final SecureTokenGenerator tokenGenerator;
    private final PasswordResetNotifier notifier;
    private final TransactionRunner tx;
    private final Clock clock;
    private final Duration tokenTtl;
    private final boolean exposeTokenInResponse;

    public RequestPasswordRecoveryUseCase(UserRepository users, PasswordResetTokenRepository resetTokens,
            SecureTokenGenerator tokenGenerator, PasswordResetNotifier notifier, TransactionRunner tx, Clock clock,
            Duration tokenTtl, boolean exposeTokenInResponse) {
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("La vigencia del token de recuperacion debe ser positiva");
        }
        this.users = users;
        this.resetTokens = resetTokens;
        this.tokenGenerator = tokenGenerator;
        this.notifier = notifier;
        this.tx = tx;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.exposeTokenInResponse = exposeTokenInResponse;
    }

    public Result request(String email) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(email)).filter(User::active);
        if (found.isEmpty()) {
            return Result.EMPTY;
        }
        User user = found.get();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(tokenTtl);
        String rawToken = tokenGenerator.generate();
        tx.inTransaction(() -> {
            // Pedir uno nuevo invalida los anteriores: solo el ultimo enlace sirve.
            resetTokens.revokeUnusedForUser(user.id(), now, PasswordResetToken.REASON_SUPERSEDED);
            return resetTokens.save(PasswordResetToken.issue(user.id(), RefreshTokenHasher.sha256Hex(rawToken), now,
                    expiresAt));
        });
        notifier.deliver(user, rawToken, expiresAt);
        return exposeTokenInResponse ? new Result(Optional.of(rawToken)) : Result.EMPTY;
    }
}
