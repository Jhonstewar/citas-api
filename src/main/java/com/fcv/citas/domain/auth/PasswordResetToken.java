package com.fcv.citas.domain.auth;

import java.time.Instant;
import java.util.Objects;

/**
 * Token de recuperacion de contraseña (RF-03, HU-006/HU-007). Solo conoce el SHA-256 del valor
 * entregado, nunca el valor en claro. Es de un solo uso: se consume ({@code usedAt}) al
 * restablecer, o se revoca ({@code revokedAt}) cuando el usuario pide uno nuevo.
 */
public record PasswordResetToken(
        Long id,
        long userId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt,
        String revokedReason) {

    /** Motivo de revocacion al emitir un token nuevo para el mismo usuario. */
    public static final String REASON_SUPERSEDED = "SUPERSEDED";

    public PasswordResetToken {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt debe ser posterior a issuedAt");
        }
    }

    public static PasswordResetToken issue(long userId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        return new PasswordResetToken(null, userId, tokenHash, issuedAt, expiresAt, null, null, null);
    }

    /** Vigente, sin consumir y sin revocar: el unico estado que permite restablecer. */
    public boolean isUsable(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    /** Consume el token. Un token no utilizable no se consume: es un error de programacion. */
    public PasswordResetToken consume(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("El token de recuperacion no es utilizable");
        }
        return new PasswordResetToken(id, userId, tokenHash, issuedAt, expiresAt, now, revokedAt, revokedReason);
    }

    public PasswordResetToken withId(Long newId) {
        return new PasswordResetToken(newId, userId, tokenHash, issuedAt, expiresAt, usedAt, revokedAt,
                revokedReason);
    }

    @Override
    public String toString() {
        return "PasswordResetToken[id=%s, userId=%d, expiresAt=%s, used=%s, revoked=%s]"
                .formatted(id, userId, expiresAt, usedAt != null, revokedAt != null);
    }
}
