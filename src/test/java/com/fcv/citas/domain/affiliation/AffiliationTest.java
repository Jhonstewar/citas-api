package com.fcv.citas.domain.affiliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** HU-009 segundo corte (D26): una vigente; cambiar o quitar la cierra con {@code ended_on}. */
class AffiliationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    @Test
    void aNewAffiliationIsCurrentAndOpen() {
        Affiliation a = Affiliation.startingToday(5, 3, MONDAY);

        assertThat(a.current()).isTrue();
        assertThat(a.startedOn()).isEqualTo(MONDAY);
        assertThat(a.endedOn()).isNull();
        assertThat(a.isFor(3)).isTrue();
        assertThat(a.isFor(4)).isFalse();
    }

    /** CA-09 / CA-10: cerrar deja de ser vigente y fija ended_on al dia del cambio; conserva el resto. */
    @Test
    void closingEndsItOnTheGivenDay() {
        Affiliation current = Affiliation.startingToday(5, 3, MONDAY).withId(11);

        Affiliation closed = current.close(MONDAY.plusDays(2));

        assertThat(closed.id()).isEqualTo(11);
        assertThat(closed.userId()).isEqualTo(5);
        assertThat(closed.epsPlanId()).isEqualTo(3);
        assertThat(closed.startedOn()).isEqualTo(MONDAY);
        assertThat(closed.current()).isFalse();
        assertThat(closed.endedOn()).isEqualTo(MONDAY.plusDays(2));
    }

    /** Cambiar dos veces el mismo dia: ended_on = started_on, que el CHECK de V2 admite. */
    @Test
    void closingOnTheSameDayItStartedIsAllowed() {
        Affiliation closed = Affiliation.startingToday(5, 3, MONDAY).withId(11).close(MONDAY);

        assertThat(closed.endedOn()).isEqualTo(MONDAY);
    }

    @Test
    void onlyACurrentAffiliationCanBeClosed() {
        Affiliation closed = Affiliation.startingToday(5, 3, MONDAY).withId(11).close(MONDAY);

        assertThatThrownBy(() -> closed.close(MONDAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAffiliationCannotEndBeforeItStarted() {
        Affiliation current = Affiliation.startingToday(5, 3, MONDAY).withId(11);

        assertThatThrownBy(() -> current.close(MONDAY.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
