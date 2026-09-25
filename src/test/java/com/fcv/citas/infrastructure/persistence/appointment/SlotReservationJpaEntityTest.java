package com.fcv.citas.infrastructure.persistence.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Hallazgo del LOOP_01 (S4, F1): fija que una reserva de slot SIEMPRE se trata como nueva.
 *
 * <p>El id de {@code slot_reservations} viene asignado (es el propio {@code slot_id}). Si
 * {@code isNew()} devolviera {@code false} —que es lo que Spring Data deduce por defecto para un id
 * no nulo—, {@code save} haria {@code merge}: un SELECT previo y, si el slot ya estuviera reservado
 * por OTRA cita, un UPDATE que SOBRESCRIBIRIA la fila ajena y se quedaria con la franja en silencio.
 * Con {@code isNew() == true} se hace {@code persist}: el INSERT choca con la PK y la doble reserva
 * es imposible (RN-01, dec-003). Si esta prueba falla, alguien reabrio la doble reserva.</p>
 */
class SlotReservationJpaEntityTest {

    @Test
    void aReservationIsAlwaysNewSoItIsInsertedAndNeverMerged() {
        SlotReservationJpaEntity reservation = SlotReservationJpaEntity.forAppointment(42L, 7L, 1);

        assertThat(reservation.getId()).isEqualTo(42L);
        assertThat(reservation.isNew()).isTrue();
    }
}
