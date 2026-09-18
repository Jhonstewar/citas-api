package com.fcv.citas.infrastructure.persistence.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSpecialtyRepository extends JpaRepository<SpecialtyJpaEntity, Integer> {

    boolean existsByCode(String code);

    List<SpecialtyJpaEntity> findAllByOrderByNameAsc();

    List<SpecialtyJpaEntity> findByActiveTrueOrderByNameAsc();
}
