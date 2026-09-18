package com.fcv.citas.infrastructure.persistence.schedule;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBlockRepository extends JpaRepository<AvailabilityBlockJpaEntity, Long> {

    List<AvailabilityBlockJpaEntity> findByProfessionalIdAndBlockDate(Long professionalId, LocalDate blockDate);
}
