package com.fcv.citas.infrastructure.persistence.affiliation;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAffiliationRepository extends JpaRepository<AffiliationJpaEntity, Long> {

    /** La afiliacion vigente del usuario (a lo sumo una: uq_affiliations_user_current), bloqueada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AffiliationJpaEntity a WHERE a.userId = :userId AND a.current = true")
    Optional<AffiliationJpaEntity> lockCurrentByUserId(@Param("userId") long userId);
}
