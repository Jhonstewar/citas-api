package com.fcv.citas.infrastructure.persistence.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fcv.citas.CitasApiApplication;
import com.fcv.citas.domain.auth.PasswordResetToken;
import com.fcv.citas.domain.auth.PasswordResetTokenRepository;

/** Adaptador JPA del puerto {@link PasswordResetTokenRepository}. */
@Component
class JpaPasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    /** Las columnas DATETIME(6) se escriben en la zona operativa, igual que sus DEFAULT de MySQL. */
    private static final ZoneId ZONE = ZoneId.of(CitasApiApplication.APP_TIME_ZONE);

    private final SpringDataPasswordResetTokenRepository repository;

    JpaPasswordResetTokenRepositoryAdapter(SpringDataPasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        if (token.id() == null) {
            PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity(token.userId(), token.tokenHash(),
                    toLocal(token.issuedAt()), toLocal(token.expiresAt()));
            entity.updateLifecycle(toLocal(token.usedAt()), toLocal(token.revokedAt()), token.revokedReason());
            return toDomain(repository.saveAndFlush(entity));
        }
        PasswordResetTokenJpaEntity entity = repository.findById(token.id())
                .orElseThrow(() -> new IllegalStateException("Token de recuperacion inexistente: id=" + token.id()));
        entity.updateLifecycle(toLocal(token.usedAt()), toLocal(token.revokedAt()), token.revokedReason());
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash).map(JpaPasswordResetTokenRepositoryAdapter::toDomain);
    }

    @Override
    public void revokeUnusedForUser(long userId, Instant now, String reason) {
        repository.revokeUnusedForUser(userId, toLocal(now), reason);
    }

    private static PasswordResetToken toDomain(PasswordResetTokenJpaEntity e) {
        return new PasswordResetToken(e.getId(), e.getUserId(), e.getTokenHash(), toInstant(e.getIssuedAt()),
                toInstant(e.getExpiresAt()), toInstant(e.getUsedAt()), toInstant(e.getRevokedAt()),
                e.getRevokedReason());
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }

    private static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(ZONE).toInstant();
    }
}
