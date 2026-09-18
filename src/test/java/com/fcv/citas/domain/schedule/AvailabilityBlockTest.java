package com.fcv.citas.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Verificacion 4 de S3 — reglas de slots (RF-08, RF-09, RN-05, RN-06), dominio puro.
 */
class AvailabilityBlockTest {

    private static final LocalDate DAY = LocalDate.of(2030, 3, 4);

    private static AvailabilityBlock block(String start, String end) {
        return new AvailabilityBlock(null, 1L, 1, DAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    /** HU-017 CA-02: 08:00–12:00 → exactamente 8 slots, sin huecos ni solapes. */
    @Test
    void morningBlockExpandsIntoEightSlotsOf30Minutes() {
        List<LocalTime> slots = block("08:00", "12:00").slotStarts();
        assertThat(slots).extracting(LocalTime::toString)
                .containsExactly("08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30");
    }

    /** PRD RF-08: un dia valido con dos bloques 08:00–12:00 y 14:00–17:00. */
    @Test
    void afternoonBlockOfThreeHoursHasSixSlots() {
        assertThat(block("14:00", "17:00").slotStarts()).hasSize(6);
    }

    @Test
    void minimalBlockHasOneSlot() {
        assertThat(block("08:00", "08:30").slotStarts()).containsExactly(LocalTime.of(8, 0));
    }

    /** Bordes de la rejilla: solo :00 y :30, sin segundos. */
    @ParameterizedTest
    @CsvSource({ "08:15,10:00", "08:00,10:45", "08:00:30,10:00" })
    void rejectsTimesOffTheHalfHourGrid(String start, String end) {
        assertThatThrownBy(() -> block(start, end)).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("30 minutos");
    }

    @ParameterizedTest
    @CsvSource({ "10:00,10:00", "10:00,09:30" })
    void rejectsEmptyOrInvertedRange(String start, String end) {
        assertThatThrownBy(() -> block(start, end)).isInstanceOf(InvalidRequestException.class)
                .extracting("field").isEqualTo("endTime");
    }

    /** HU-017 CA-04: solape con otro bloque del mismo dia; contiguos no solapan. */
    @Test
    void detectsOverlapButAllowsAdjacentBlocks() {
        AvailabilityBlock morning = block("08:00", "12:00");
        assertThat(morning.overlaps(block("11:30", "13:00"))).isTrue();
        assertThat(morning.overlaps(block("07:00", "08:30"))).isTrue();
        assertThat(morning.overlaps(block("09:00", "10:00"))).isTrue();
        assertThat(morning.overlaps(block("12:00", "14:00"))).isFalse();
        assertThat(morning.overlaps(block("06:00", "08:00"))).isFalse();
        AvailabilityBlock otherDay = new AvailabilityBlock(null, 1L, 1, DAY.plusDays(1), LocalTime.of(8, 0),
                LocalTime.of(12, 0));
        assertThat(morning.overlaps(otherDay)).isFalse();
    }

    /** RN-06: un bloque que ya empezo es pasado. */
    @Test
    void isPastOnceItHasStarted() {
        AvailabilityBlock b = block("08:00", "12:00");
        assertThat(b.hasStartedAt(LocalDateTime.of(DAY, LocalTime.of(7, 59)))).isFalse();
        assertThat(b.hasStartedAt(LocalDateTime.of(DAY, LocalTime.of(8, 0)))).isTrue();
        assertThat(b.hasStartedAt(LocalDateTime.of(DAY.plusDays(1), LocalTime.MIDNIGHT))).isTrue();
    }

    /** RN-05 / D9: una cita de 60 min necesita el slot siguiente dentro del MISMO bloque. */
    @Test
    void sixtyMinutesNeedsTheNextSlotInsideTheSameBlock() {
        AvailabilityBlock b = block("08:00", "10:00");
        assertThat(b.canHost(LocalTime.of(9, 0), 2)).isTrue();
        assertThat(b.canHost(LocalTime.of(9, 30), 2)).isFalse();
        assertThat(b.canHost(LocalTime.of(9, 30), 1)).isTrue();
        assertThat(b.canHost(LocalTime.of(10, 0), 1)).isFalse();
        assertThat(b.canHost(LocalTime.of(8, 15), 1)).isFalse();
    }
}
