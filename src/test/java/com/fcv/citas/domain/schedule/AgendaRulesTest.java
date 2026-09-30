package com.fcv.citas.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LOOP_03: las dos reglas de agenda tienen un unico sitio, {@link AgendaRules}. */
class AgendaRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 3, 4, 10, 0);

    /** "Ya empezo": el inicio no es posterior a ahora (el instante exacto cuenta como empezado). */
    @Test
    void hasStartedIsTrueAtAndAfterTheStartInstant() {
        assertThat(AgendaRules.hasStarted(NOW.minusMinutes(1), NOW)).isTrue();
        assertThat(AgendaRules.hasStarted(NOW, NOW)).isTrue();
        assertThat(AgendaRules.hasStarted(NOW.plusNanos(1), NOW)).isFalse();
    }

    /** Bloque 08:00-10:00: encaje de {@code slots} slots consecutivos dentro del bloque. */
    @ParameterizedTest
    @CsvSource({
            "08:00,1,true", "08:00,2,true", "09:00,2,true", "09:30,1,true", "09:30,2,false",
            "10:00,1,false", "07:30,1,false", "08:15,1,false", "08:00:30,1,false", "08:00,0,false",
            "08:00,5,false" })
    void fitsRequiresGridStartInsideTheBlockAndConsecutiveSlots(String start, int slots, boolean expected) {
        assertThat(AgendaRules.fits(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.parse(start), slots))
                .isEqualTo(expected);
    }
}
