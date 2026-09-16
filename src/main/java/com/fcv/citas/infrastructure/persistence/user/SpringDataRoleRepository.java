package com.fcv.citas.infrastructure.persistence.user;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataRoleRepository extends JpaRepository<RoleJpaEntity, Short> {

    Set<RoleJpaEntity> findByCodeIn(Collection<String> codes);
}
