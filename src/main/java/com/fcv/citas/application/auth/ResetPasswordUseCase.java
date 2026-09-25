package com.fcv.citas.application.auth;

import java.time.Clock;
import java.time.Instant;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.InvalidResetTokenException;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.auth.PasswordPolicy;
import com.fcv.citas.domain.auth.PasswordResetToken;
import com.fcv.citas.domain.auth.PasswordResetTokenRepository;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.fcv.citas.domain.auth.RefreshTokenRepository;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/**
 * HU-007: restablecer la contraseña presentando el token de recuperacion.
 *
 * <p>En UNA transaccion: se bloquea el token por su hash, se comprueba que siga utilizable, se
 * cambia el hash de la contraseña, se consume el token y se revocan TODAS las familias de refresh
 * token del usuario (D34: una contraseña robada no sobrevive en sesiones abiertas). O pasa todo o
 * no pasa nada: no existe un resultado con la contraseña cambiada y el token vivo, ni al reves.</p>
 *
 * <p>La politica D29 se comprueba ANTES de tocar el token: una contraseña que no cumple no lo
 * consume (HU-007 CA-08). Token inexistente, caducado, usado o revocado, y cuenta inactiva, dan
 * la misma {@link InvalidResetTokenException}.</p>
 */
public class ResetPasswordUseCase {

    static final String NEW_PASSWORD_FIELD = "newPassword"; // secret-scan:allow nombre del campo del cuerpo, no una credencial

    private final PasswordResetTokenRepository resetTokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final TransactionRunner tx;
    private final Clock clock;

    public ResetPasswordUseCase(PasswordResetTokenRepository resetTokens, UserRepository users,
            RefreshTokenRepository refreshTokens, PasswordHasher passwordHasher, TransactionRunner tx, Clock clock) {
        this.resetTokens = resetTokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordHasher = passwordHasher;
        this.tx = tx;
        this.clock = clock;
    }

    public void reset(String rawToken, String newPassword) {
        PasswordPolicy.require(newPassword, NEW_PASSWORD_FIELD);
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidResetTokenException();
        }
        // El hash (BCrypt, deliberadamente lento) se calcula fuera de la transaccion para no
        // retener el bloqueo de la fila; y se calcula siempre, sea valido o no el token, para que
        // el tiempo de respuesta no distinga un token existente de uno inventado.
        String newHash = passwordHasher.hash(newPassword);
        String tokenHash = RefreshTokenHasher.sha256Hex(rawToken);
        tx.inTransaction(() -> {
            Instant now = clock.instant();
            PasswordResetToken token = resetTokens.findByTokenHashForUpdate(tokenHash)
                    .filter(t -> t.isUsable(now))
                    .orElseThrow(InvalidResetTokenException::new);
            User user = users.findById(token.userId()).filter(User::active)
                    .orElseThrow(InvalidResetTokenException::new);
            users.updatePasswordHash(user.id(), newHash);
            resetTokens.save(token.consume(now));
            refreshTokens.revokeAllForUser(user.id(), now, RefreshToken.REASON_PASSWORD_RESET);
            return null;
        });
    }
}
