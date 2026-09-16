package com.fcv.citas.infrastructure.persistence.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fcv.citas.CitasApiApplication;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenRepository;

/** Adaptador JPA del puerto {@link RefreshTokenRepository}. */
@Component
class JpaRefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    /** Las columnas DATETIME(6) se escriben en la zona operativa, igual que sus DEFAULT de MySQL. */
    private static final ZoneId ZONE = ZoneId.of(CitasApiApplication.APP_TIME_ZONE);

    private final SpringDataRefreshTokenRepository repository;

    JpaRefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        if (token.id() == null) {
            RefreshTokenJpaEntity saved = repository.saveAndFlush(new RefreshTokenJpaEntity(null, token.userId(),
                    token.tokenHash(), token.familyId(), toLocal(token.issuedAt()), toLocal(token.expiresAt()),
                    toLocal(token.usedAt()), toLocal(token.revokedAt()), token.revokedReason(),
                    token.replacedById()));
            return toDomain(saved);
        }
        RefreshTokenJpaEntity entity = repository.findById(token.id())
                .orElseThrow(() -> new IllegalStateException("Refresh token inexistente: id=" + token.id()));
        entity.updateLifecycle(toLocal(token.usedAt()), toLocal(token.revokedAt()), token.revokedReason(),
                token.replacedById());
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash).map(JpaRefreshTokenRepositoryAdapter::toDomain);
    }

    @Override
    public void revokeFamily(String familyId, Instant now, String reason) {
        repository.revokeFamily(familyId, toLocal(now), reason);
    }

    private static RefreshToken toDomain(RefreshTokenJpaEntity e) {
        return new RefreshToken(e.getId(), e.getUserId(), e.getTokenHash(), e.getFamilyId(),
                toInstant(e.getIssuedAt()), toInstant(e.getExpiresAt()), toInstant(e.getUsedAt()),
                toInstant(e.getRevokedAt()), e.getRevokedReason(), e.getReplacedByTokenId());
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }

    private static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(ZONE).toInstant();
    }
}
