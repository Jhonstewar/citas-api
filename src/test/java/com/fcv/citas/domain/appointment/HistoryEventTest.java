package com.fcv.citas.domain.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * D39: la regla que deriva el evento de una fila del historial, sin columna ni migracion nuevas. Es
 * la unica definicion de "esta fila es una reprogramacion aprobada" y se prueba sin framework.
 */
class HistoryEventTest {

    /** La primera fila del historial no tiene anterior: nunca es un evento. */
    @Test
    void theFirstRowIsNeverAnEvent() {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            assertThat(HistoryEvent.between(null, status)).isNull();
        }
    }

    /**
     * Una fila que REPITE el estado de la anterior solo puede venir de una reprogramacion aprobada. Aqui
     * se prueba el ultimo eslabon de esa cadena: ninguna transicion declarada va de un estado a si
     * mismo, asi que {@link Appointment#transitionTo} nunca escribe una fila que repita el estado. Que
     * {@link Appointment#rescheduleTo} sea la UNICA productora de filas que lo repiten lo fija
     * {@link HistoryRowInvariantTest}, y que nadie mas construya filas de historial,
     * {@code HistoryWritersArchitectureTest}. Ojo: la reprogramacion NO es la unica escritura que se
     * salta {@code transitionTo} —las dos fabricas de {@code Appointment} tambien lo hacen—, por eso el
     * argumento completo esta en {@link HistoryEvent#between}.
     */
    @Test
    void repeatingThePreviousStatusIsAnApprovedReschedule() {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("ninguna transicion va a si misma").isFalse();
            assertThat(HistoryEvent.between(status, status)).isEqualTo(HistoryEvent.RESCHEDULED);
        }
    }

    /** Un cambio de estado real (lo que escribe toda transicion) no lleva evento. */
    @Test
    void aRealStatusChangeHasNoEvent() {
        for (AppointmentStatus from : AppointmentStatus.values()) {
            for (AppointmentStatus to : from.allowedNext()) {
                assertThat(HistoryEvent.between(from, to)).isNull();
            }
        }
        assertThat(HistoryEvent.between(AppointmentStatus.REQUESTED, AppointmentStatus.APPROVED)).isNull();
        assertThat(HistoryEvent.between(AppointmentStatus.APPROVED, AppointmentStatus.COMPLETED)).isNull();
    }

    /** RESCHEDULED es el unico evento del contrato S4 ({@code event?: 'RESCHEDULED'}). */
    @Test
    void rescheduledIsTheOnlyEvent() {
        assertThat(HistoryEvent.values()).containsExactly(HistoryEvent.RESCHEDULED);
    }
}
