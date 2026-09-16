package com.fcv.citas.application.auth;

import java.time.Clock;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.fcv.citas.domain.auth.RefreshTokenRepository;

/**
 * HU-004 + DEC-002: logout revoca la familia del refresh token presentado. Idempotente y sin
 * resultado observable distinto si el token no existe o ya estaba revocado.
 */
public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final TransactionRunner tx;
    private final Clock clock;

    public LogoutUseCase(RefreshTokenRepository refreshTokens, TransactionRunner tx, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.tx = tx;
        this.clock = clock;
    }

    public void logout(String rawRefreshToken) {
        tx.inTransaction(() -> {
            refreshTokens.findByTokenHashForUpdate(RefreshTokenHasher.sha256Hex(rawRefreshToken))
                    .filter(token -> !token.isRevoked())
                    .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), clock.instant(),
                            RefreshToken.REASON_LOGOUT));
            return null;
        });
    }
}
