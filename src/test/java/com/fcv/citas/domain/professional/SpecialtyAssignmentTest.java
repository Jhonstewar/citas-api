package com.fcv.citas.domain.professional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.shared.InvalidRequestException;

/** HU-014: forma del conjunto de especialidades (dominio puro, sin Spring). */
class SpecialtyAssignmentTest {

    @Test
    void acceptsASetWithOnePrimaryInsideIt() {
        SpecialtyAssignment a = SpecialtyAssignment.of(List.of(1, 2), 2);
        assertThat(a.specialtyIds()).containsExactlyInAnyOrder(1, 2);
        assertThat(a.primarySpecialtyId()).isEqualTo(2);
    }

    @Test
    void rejectsEmptySet() {
        assertThatThrownBy(() -> SpecialtyAssignment.of(List.of(), 1))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("field").isEqualTo("specialtyIds");
    }

    @Test
    void rejectsMissingPrimary() {
        assertThatThrownBy(() -> SpecialtyAssignment.of(List.of(1), null))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("field").isEqualTo("primarySpecialtyId");
    }

    @Test
    void rejectsPrimaryOutsideTheSet() {
        assertThatThrownBy(() -> SpecialtyAssignment.of(List.of(1, 2), 3))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("primaria");
    }

    @Test
    void rejectsDuplicatesAndNulls() {
        assertThatThrownBy(() -> SpecialtyAssignment.of(List.of(1, 1), 1)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> SpecialtyAssignment.of(Arrays.asList(1, null), 1))
                .isInstanceOf(InvalidRequestException.class);
    }
}
