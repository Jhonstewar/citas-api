package com.fcv.citas.application.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.InvalidRefreshTokenException;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.fcv.citas.domain.auth.RefreshTokenRepository;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/**
 * HU-003 + DEC-002: renovacion con rotacion. El token presentado se consume y se emite otro en
 * la misma familia. Presentar un token ya consumido revoca la familia completa.
 */
public class RefreshSessionUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final SessionIssuer sessionIssuer;
    private final TransactionRunner tx;
    private final Clock clock;

    public RefreshSessionUseCase(RefreshTokenRepository refreshTokens, UserRepository users,
            SessionIssuer sessionIssuer, TransactionRunner tx, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.sessionIssuer = sessionIssuer;
        this.tx = tx;
        this.clock = clock;
    }

    public AuthSession refresh(String rawRefreshToken) {
        // La transaccion devuelve un Optional vacio en lugar de lanzar para que la revocacion de la
        // familia por reuso se confirme (commit) antes de rechazar la peticion.
        Optional<AuthSession> result = tx.inTransaction(() -> attempt(rawRefreshToken));
        return result.orElseThrow(InvalidRefreshTokenException::new);
    }

    private Optional<AuthSession> attempt(String rawRefreshToken) {
        Instant now = clock.instant();
        Optional<RefreshToken> found =
                refreshTokens.findByTokenHashForUpdate(RefreshTokenHasher.sha256Hex(rawRefreshToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        if (token.isRevoked()) {
            return Optional.empty();
        }
        if (token.isUsed()) {
            refreshTokens.revokeFamily(token.familyId(), now, RefreshToken.REASON_REUSE_DETECTED);
            return Optional.empty();
        }
        if (token.isExpired(now)) {
            return Optional.empty();
        }
        Optional<User> user = users.findById(token.userId()).filter(User::active);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        SessionIssuer.Issued issued = sessionIssuer.issue(user.get(), token.familyId());
        refreshTokens.save(token.markUsed(now, issued.refreshTokenId()));
        return Optional.of(issued.session());
    }
}
