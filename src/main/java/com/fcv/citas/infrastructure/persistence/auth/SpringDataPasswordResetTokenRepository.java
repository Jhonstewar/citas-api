package com.fcv.citas.infrastructure.persistence.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataPasswordResetTokenRepository extends JpaRepository<PasswordResetTokenJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetTokenJpaEntity t where t.tokenHash = :hash")
    Optional<PasswordResetTokenJpaEntity> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetTokenJpaEntity t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.userId = :userId and t.usedAt is null and t.revokedAt is null
            """)
    int revokeUnusedForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now,
            @Param("reason") String reason);
}
