package com.fcv.citas.infrastructure.persistence.appointment;

import org.springframework.data.repository.Repository;

/**
 * Solo insercion: extiende {@link Repository} (no {@code JpaRepository}) para que no exista ningun
 * metodo de borrado ni de actualizacion del historial (RN-12, HU-032 CA-06).
 */
interface SpringDataStatusHistoryRepository extends Repository<StatusHistoryJpaEntity, Long> {

    StatusHistoryJpaEntity save(StatusHistoryJpaEntity entry);
}
