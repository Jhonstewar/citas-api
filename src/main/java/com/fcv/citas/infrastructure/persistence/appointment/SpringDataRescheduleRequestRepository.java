package com.fcv.citas.infrastructure.persistence.appointment;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRescheduleRequestRepository extends JpaRepository<RescheduleRequestJpaEntity, Long> {

    /** {@code SELECT ... FOR UPDATE}: dos decisiones sobre la misma solicitud se serializan (HU-031 CA-07). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RescheduleRequestJpaEntity r WHERE r.id = :id")
    Optional<RescheduleRequestJpaEntity> lockById(@Param("id") Long id);

    /**
     * La solicitud sin decidir de la cita, bloqueada. "Sin decidir" = {@code decided_by_user_id IS NULL},
     * que el CHECK {@code ck_reschedule_requests_decision} (V3) ata a {@code decided_at IS NULL}: lo mismo
     * que {@code active_marker}, asi que como mucho hay una.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RescheduleRequestJpaEntity r WHERE r.appointmentId = :appointmentId"
            + " AND r.decidedByUserId IS NULL")
    Optional<RescheduleRequestJpaEntity> lockPendingByAppointment(@Param("appointmentId") Long appointmentId);
}
