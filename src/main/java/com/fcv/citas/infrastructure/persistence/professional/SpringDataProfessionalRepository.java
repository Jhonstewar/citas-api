package com.fcv.citas.infrastructure.persistence.professional;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProfessionalRepository extends JpaRepository<ProfessionalJpaEntity, Long> {

    boolean existsByProfessionalCode(String professionalCode);

    boolean existsByLicenseNumber(String licenseNumber);

    Optional<ProfessionalJpaEntity> findByUserId(Long userId);
}
