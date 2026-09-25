package com.fcv.citas.infrastructure.persistence.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Libro unico de reservas (dec-003). Solo inserta: la liberacion tiene un unico camino,
 * {@code JpaAppointmentRepositoryAdapter#releaseReservations}, para que cancelar, rechazar una cita
 * y rechazar una reprogramacion no diverjan.
 */
interface SpringDataSlotReservationRepository extends JpaRepository<SlotReservationJpaEntity, Long> {
}
