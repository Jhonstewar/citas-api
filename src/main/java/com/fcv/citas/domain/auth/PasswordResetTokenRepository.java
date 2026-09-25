package com.fcv.citas.domain.auth;

import java.time.Instant;
import java.util.Optional;

/** Puerto de persistencia de los tokens de recuperacion hasheados ({@code password_reset_tokens}). */
public interface PasswordResetTokenRepository {

    /** Inserta (id nulo) o actualiza el ciclo de vida del registro. Devuelve el token con id. */
    PasswordResetToken save(PasswordResetToken token);

    /** Busca por hash bloqueando el registro: dos restablecimientos simultaneos no lo consumen dos veces. */
    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    /** Revoca los tokens del usuario que sigan sin consumir ni revocar. */
    void revokeUnusedForUser(long userId, Instant now, String reason);
}
