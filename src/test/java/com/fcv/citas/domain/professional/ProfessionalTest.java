package com.fcv.citas.domain.professional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.shared.InvalidRequestException;

/** HU-015 y RN-07: sedes del profesional (dominio puro). */
class ProfessionalTest {

    private static final SpecialtyAssignment ONE = SpecialtyAssignment.of(List.of(1), 1);

    /**
     * Regresion (S3, F3): {@code Set.copyOf(...).contains(null)} lanza NPE. Un conjunto de sedes ya
     * inmutable debe validarse sin romperse.
     */
    @Test
    void acceptsAnAlreadyImmutableSiteSet() {
        Professional p = new Professional(null, 1, "p-1", "lic-1", true, ONE, Set.copyOf(Set.of(1, 2)));
        assertThat(p.siteIds()).containsExactlyInAnyOrder(1, 2);
        assertThat(p.worksAt(2)).isTrue();
        assertThat(p.worksAt(3)).isFalse();
        assertThat(p.professionalCode()).isEqualTo("P-1");
    }

    @Test
    void rejectsEmptyOrNullSites() {
        assertThatThrownBy(() -> Professional.requireSites(Set.of())).isInstanceOf(InvalidRequestException.class);
        Set<Integer> withNull = new HashSet<>();
        withNull.add(null);
        assertThatThrownBy(() -> Professional.requireSites(withNull)).isInstanceOf(InvalidRequestException.class);
    }

    /**
     * HU-016 (DoD, R2): activar y desactivar son operaciones explicitas del dominio. Solo cambian el
     * estado; especialidades, sedes, codigo y matricula se conservan (CA-01, CA-02).
     */
    @Test
    void deactivateAndActivateOnlyChangeTheActiveFlag() {
        Professional active = new Professional(7L, 1, "P-1", "LIC-1", true, ONE, Set.of(1, 2));

        Professional inactive = active.deactivate();
        assertThat(inactive.active()).isFalse();
        assertThat(inactive).usingRecursiveComparison().ignoringFields("active").isEqualTo(active);

        Professional reactivated = inactive.activate();
        assertThat(reactivated.active()).isTrue();
        assertThat(reactivated).isEqualTo(active);
    }

    /** Idempotentes: repetir la operacion no es un error (PATCH /status responde 200 igual). */
    @Test
    void activationIsIdempotent() {
        Professional active = new Professional(7L, 1, "P-1", "LIC-1", true, ONE, Set.of(1));
        assertThat(active.activate()).isEqualTo(active);
        assertThat(active.deactivate().deactivate().active()).isFalse();
    }
}
