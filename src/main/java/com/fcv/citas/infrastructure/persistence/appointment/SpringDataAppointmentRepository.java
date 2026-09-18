package com.fcv.citas.infrastructure.persistence.appointment;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAppointmentRepository extends JpaRepository<AppointmentJpaEntity, Long> {

    /** {@code SELECT ... FOR UPDATE}: dos decisiones sobre la misma cita se serializan (HU-030 CA-07). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AppointmentJpaEntity a WHERE a.id = :id")
    Optional<AppointmentJpaEntity> lockById(@Param("id") Long id);
}
