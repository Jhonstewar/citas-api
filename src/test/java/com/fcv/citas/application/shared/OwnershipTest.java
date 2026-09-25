package com.fcv.citas.application.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.shared.NotFoundException;

/**
 * HU-005 (R3): una unica politica de ownership. Un recurso ajeno responde exactamente igual que uno
 * inexistente (mismo tipo, mismo {@code code}, mismo mensaje), para no revelar que existe (CA-05).
 */
class OwnershipTest {

    private record Resource(long id, long ownerId) {
    }

    private static final String MISSING = "La cita no existe";

    @Test
    void returnsTheResourceWhenItBelongsToTheRequester() {
        Resource mine = new Resource(1, 10);
        assertThat(Ownership.requireOwned(Optional.of(mine), Resource::ownerId, 10, MISSING)).isSameAs(mine);
    }

    @Test
    void anotherOwnersResourceIsIndistinguishableFromAMissingOne() {
        Throwable foreign = catchNotFound(() -> Ownership.requireOwned(Optional.of(new Resource(1, 20)),
                Resource::ownerId, 10, MISSING));
        Throwable missing = catchNotFound(() -> Ownership.requireOwned(Optional.<Resource>empty(),
                Resource::ownerId, 10, MISSING));

        assertThat(foreign).isInstanceOf(NotFoundException.class).hasMessage(MISSING);
        assertThat(missing).isInstanceOf(NotFoundException.class).hasMessage(MISSING);
        assertThat(((NotFoundException) foreign).code()).isEqualTo(((NotFoundException) missing).code())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void theOwnerIsNeverReadFromAMissingResource() {
        assertThatThrownBy(() -> Ownership.requireOwned(Optional.<Resource>empty(), r -> {
            throw new AssertionError("no debe consultarse el titular de un recurso inexistente");
        }, 10, MISSING)).isInstanceOf(NotFoundException.class);
    }

    private static Throwable catchNotFound(Runnable action) {
        try {
            action.run();
        } catch (NotFoundException e) {
            return e;
        }
        throw new AssertionError("se esperaba NotFoundException");
    }
}
