package com.fcv.citas.infrastructure.persistence.professional;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataProfessionalSiteRepository extends JpaRepository<ProfessionalSiteJpaEntity, ProfessionalSiteJpaEntity.Key> {

    List<ProfessionalSiteJpaEntity> findByIdProfessionalId(Long professionalId);

    /**
     * Borrado inmediato (no diferido al flush): Hibernate ejecuta los INSERT antes que los DELETE
     * de la misma unidad de trabajo, y reasignar el mismo valor chocaria con la clave primaria.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProfessionalSiteJpaEntity e WHERE e.id.professionalId = :professionalId")
    void deleteAllOfProfessional(@Param("professionalId") Long professionalId);
}
