package com.fcv.citas.infrastructure.persistence.catalog;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAppointmentTypeRepository extends JpaRepository<AppointmentTypeJpaEntity, Short> {

    Optional<AppointmentTypeJpaEntity> findByCode(String code);
}
