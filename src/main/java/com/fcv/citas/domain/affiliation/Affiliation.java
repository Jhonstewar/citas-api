package com.fcv.citas.domain.affiliation;

import java.time.LocalDate;

/**
 * Afiliacion de un usuario a un plan de EPS (RF-04). Referencia solo al plan: la EPS y el regimen
 * se conocen navegando desde el, y no se copian aqui (3FN, ver HU-009).
 *
 * <p>{@code id} es nulo mientras la afiliacion no se ha persistido. El numero de afiliado y la
 * fecha de fin no se piden en el registro (HU-009 acotada a esa ruta), por eso no viajan en el
 * modelo: la fila los deja nulos.</p>
 */
public record Affiliation(Long id, long userId, int epsPlanId, boolean current, LocalDate startedOn) {

    public Affiliation {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId invalido");
        }
        if (epsPlanId <= 0) {
            throw new IllegalArgumentException("epsPlanId invalido");
        }
        if (startedOn == null) {
            throw new IllegalArgumentException("startedOn es obligatorio");
        }
    }

    /** Afiliacion vigente que empieza hoy; es la unica forma que crea el registro (HU-009). */
    public static Affiliation startingToday(long userId, int epsPlanId, LocalDate today) {
        return new Affiliation(null, userId, epsPlanId, true, today);
    }

    public Affiliation withId(long assignedId) {
        return new Affiliation(assignedId, userId, epsPlanId, current, startedOn);
    }
}
