package com.fcv.citas.domain.eps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.shared.InvalidRequestException;

/** HU-012: EPS y plan como dominio puro (DoD: sin Spring ni JPA), con activar/desactivar como operaciones. */
class EpsTest {

    @Test
    void anEpsIsCreatedActiveWithANormalizedCodeAndATrimmedName() {
        Eps eps = Eps.create("  eps demo ", "  Salud Demo  ");

        assertThat(eps.id()).isNull();
        assertThat(eps.code()).isEqualTo("EPS_DEMO");
        assertThat(eps.name()).isEqualTo("Salud Demo");
        assertThat(eps.active()).isTrue();
    }

    @Test
    void codeAndNameAreMandatoryAndBounded() {
        assertField(() -> Eps.create(" ", "Salud"), "code");
        assertField(() -> Eps.create(null, "Salud"), "code");
        assertField(() -> Eps.create("X".repeat(Eps.MAX_CODE + 1), "Salud"), "code");
        assertField(() -> Eps.create("EPS", ""), "name");
        assertField(() -> Eps.create("EPS", "N".repeat(Eps.MAX_NAME + 1)), "name");
        assertThat(Eps.create("X".repeat(Eps.MAX_CODE), "N".repeat(Eps.MAX_NAME)).code()).hasSize(Eps.MAX_CODE);
    }

    @Test
    void renameKeepsCodeAndState() {
        Eps eps = new Eps(7, "EPS_DEMO", "Salud Demo", false);

        Eps renamed = eps.rename(" Salud Nueva ");

        assertThat(renamed).isEqualTo(new Eps(7, "EPS_DEMO", "Salud Nueva", false));
        assertField(() -> eps.rename(" "), "name");
    }

    @Test
    void activateAndDeactivateAreOperations() {
        Eps eps = new Eps(7, "EPS_DEMO", "Salud Demo", true);

        assertThat(eps.deactivate().active()).isFalse();
        assertThat(eps.deactivate().activate().active()).isTrue();
        assertThat(eps.deactivate().activate()).isEqualTo(eps);
    }

    // ------------------------------------------------------------------ planes

    @Test
    void aPlanIsCreatedActiveWithItsRegime() {
        EpsPlan plan = EpsPlan.create(7, " plan basico ", " Plan básico ", " contributivo ");

        assertThat(plan.id()).isNull();
        assertThat(plan.epsId()).isEqualTo(7);
        assertThat(plan.code()).isEqualTo("PLAN_BASICO");
        assertThat(plan.name()).isEqualTo("Plan básico");
        assertThat(plan.regimeCode()).isEqualTo("CONTRIBUTIVO");
        assertThat(plan.active()).isTrue();
    }

    /** HU-012 CA-02: el plan exige su regimen (el catalogo fijo lo comprueba el caso de uso). */
    @Test
    void aPlanRequiresCodeNameAndRegime() {
        assertField(() -> EpsPlan.create(7, "", "Plan", "CONTRIBUTIVO"), "code");
        assertField(() -> EpsPlan.create(7, "C".repeat(EpsPlan.MAX_CODE + 1), "Plan", "CONTRIBUTIVO"), "code");
        assertField(() -> EpsPlan.create(7, "P", " ", "CONTRIBUTIVO"), "name");
        assertField(() -> EpsPlan.create(7, "P", "N".repeat(EpsPlan.MAX_NAME + 1), "CONTRIBUTIVO"), "name");
        assertField(() -> EpsPlan.create(7, "P", "Plan", null), "regimeCode");
        assertField(() -> EpsPlan.create(7, "P", "Plan", "  "), "regimeCode");
        assertThatThrownBy(() -> EpsPlan.create(0, "P", "Plan", "CONTRIBUTIVO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planDetailsAndStateChangeWithoutTouchingCodeOrEps() {
        EpsPlan plan = new EpsPlan(3, 7, "P_A", "Plan A", "CONTRIBUTIVO", true);

        EpsPlan edited = plan.withDetails(" Plan A+ ", "subsidiado");

        assertThat(edited).isEqualTo(new EpsPlan(3, 7, "P_A", "Plan A+", "SUBSIDIADO", true));
        assertThat(plan.deactivate().active()).isFalse();
        assertThat(plan.deactivate().activate()).isEqualTo(plan);
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> {
                    assertThat(e.field()).isEqualTo(field);
                    assertThat(e.code()).isEqualTo("VALIDATION");
                });
    }
}
