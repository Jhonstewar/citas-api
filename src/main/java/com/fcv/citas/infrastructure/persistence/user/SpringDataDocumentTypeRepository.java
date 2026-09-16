package com.fcv.citas.infrastructure.persistence.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataDocumentTypeRepository extends JpaRepository<DocumentTypeJpaEntity, Short> {

    Optional<DocumentTypeJpaEntity> findByCodeAndActiveTrue(String code);
}
