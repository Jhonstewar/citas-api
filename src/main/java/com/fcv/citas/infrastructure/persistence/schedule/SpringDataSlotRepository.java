package com.fcv.citas.infrastructure.persistence.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataSlotRepository extends JpaRepository<AvailabilitySlotJpaEntity, Long> {

    /** Borrado inmediato: regenerar los slots reinserta inicios que chocarian con la clave unica. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AvailabilitySlotJpaEntity s WHERE s.blockId = :blockId")
    void deleteAllOfBlock(@Param("blockId") Long blockId);
}
