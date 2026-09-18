package com.fcv.citas.infrastructure.persistence.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSpecialtyRepository extends JpaRepository<SpecialtyJpaEntity, Integer> {

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    List<SpecialtyJpaEntity> findAllByOrderByNameAsc();

    List<SpecialtyJpaEntity> findByActiveTrueOrderByNameAsc();
}
