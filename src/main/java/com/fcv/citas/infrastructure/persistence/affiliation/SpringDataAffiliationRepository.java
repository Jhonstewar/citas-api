package com.fcv.citas.infrastructure.persistence.affiliation;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAffiliationRepository extends JpaRepository<AffiliationJpaEntity, Long> {
}
