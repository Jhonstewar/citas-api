package com.fcv.citas.infrastructure.persistence.eps;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEpsRepository extends JpaRepository<EpsJpaEntity, Integer> {
}
