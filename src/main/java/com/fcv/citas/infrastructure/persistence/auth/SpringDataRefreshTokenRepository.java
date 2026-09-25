package com.fcv.citas.infrastructure.persistence.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenJpaEntity t where t.tokenHash = :hash")
    Optional<RefreshTokenJpaEntity> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshTokenJpaEntity t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now,
            @Param("reason") String reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshTokenJpaEntity t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.userId = :userId and t.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now,
            @Param("reason") String reason);
}
