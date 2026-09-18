package com.fcv.citas.infrastructure.persistence.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataSlotReservationRepository extends JpaRepository<SlotReservationJpaEntity, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SlotReservationJpaEntity r WHERE r.appointmentId = :appointmentId")
    int deleteByAppointment(@Param("appointmentId") Long appointmentId);
}
