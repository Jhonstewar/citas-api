package com.fcv.citas.infrastructure.persistence.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {

    boolean existsByEmail(String email);

    boolean existsByDocumentNumber(String documentNumber);

    Optional<UserJpaEntity> findByEmail(String email);
}
