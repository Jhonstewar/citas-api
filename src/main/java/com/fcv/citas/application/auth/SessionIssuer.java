package com.fcv.citas.application.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.fcv.citas.domain.auth.AccessTokenIssuer;
import com.fcv.citas.domain.auth.IssuedAccessToken;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.fcv.citas.domain.auth.RefreshTokenRepository;
import com.fcv.citas.domain.auth.SecureTokenGenerator;
import com.fcv.citas.domain.user.User;

/**
 * Emite un access token y un refresh token opaco dentro de una familia. Debe invocarse dentro
 * de una transaccion abierta por el caso de uso.
 */
public class SessionIssuer {

    /** Resultado interno: la sesion para el cliente y el id del refresh token persistido. */
    public record Issued(AuthSession session, long refreshTokenId) {
    }

    private final AccessTokenIssuer accessTokenIssuer;
    private final SecureTokenGenerator tokenGenerator;
    private final RefreshTokenRepository refreshTokens;
    private final Duration refreshTtl;
    private final Clock clock;

    public SessionIssuer(AccessTokenIssuer accessTokenIssuer, SecureTokenGenerator tokenGenerator,
            RefreshTokenRepository refreshTokens, Duration refreshTtl, Clock clock) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenGenerator = tokenGenerator;
        this.refreshTokens = refreshTokens;
        this.refreshTtl = refreshTtl;
        this.clock = clock;
    }

    public Issued issue(User user, String familyId) {
        Instant now = clock.instant();
        IssuedAccessToken access = accessTokenIssuer.issue(user, now);
        String rawRefresh = tokenGenerator.generate();
        RefreshToken saved = refreshTokens.save(RefreshToken.issue(user.id(),
                RefreshTokenHasher.sha256Hex(rawRefresh), familyId, now, now.plus(refreshTtl)));
        long expiresIn = Math.max(0, Duration.between(now, access.expiresAt()).toSeconds());
        return new Issued(new AuthSession(access.value(), rawRefresh, expiresIn), saved.id());
    }
}
