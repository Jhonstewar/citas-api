package com.fcv.citas.infrastructure.persistence.eps;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEpsPlanRepository extends JpaRepository<EpsPlanJpaEntity, Integer> {
}
