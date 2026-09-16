package com.fcv.citas.domain.auth;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de un refresh token rotativo (DEC-002). Solo conoce el hash, nunca el valor en claro.
 */
public record RefreshToken(
        Long id,
        long userId,
        String tokenHash,
        String familyId,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt,
        String revokedReason,
        Long replacedById) {

    public static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    public static final String REASON_LOGOUT = "LOGOUT";

    public RefreshToken {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static RefreshToken issue(long userId, String tokenHash, String familyId, Instant issuedAt,
            Instant expiresAt) {
        return new RefreshToken(null, userId, tokenHash, familyId, issuedAt, expiresAt, null, null, null, null);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** Consume el token al rotarlo: queda enlazado con su reemplazo. */
    public RefreshToken markUsed(Instant now, long replacementId) {
        return new RefreshToken(id, userId, tokenHash, familyId, issuedAt, expiresAt, now, revokedAt, revokedReason,
                replacementId);
    }

    public RefreshToken withId(Long newId) {
        return new RefreshToken(newId, userId, tokenHash, familyId, issuedAt, expiresAt, usedAt, revokedAt,
                revokedReason, replacedById);
    }

    public RefreshToken revoke(Instant now, String reason) {
        return isRevoked() ? this
                : new RefreshToken(id, userId, tokenHash, familyId, issuedAt, expiresAt, usedAt, now, reason,
                        replacedById);
    }
}
