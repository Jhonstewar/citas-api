package com.fcv.citas.domain.affiliation;

import java.time.LocalDate;

/**
 * Afiliacion de un usuario a un plan de EPS (RF-04). Referencia solo al plan: la EPS y el regimen
 * se conocen navegando desde el, y no se copian aqui (3FN, ver HU-009).
 *
 * <p>D26 (HU-009 segundo corte): un usuario tiene como mucho UNA afiliacion vigente. Cambiar de plan
 * o quitarla no borra nada: {@link #close} la deja no vigente con {@code endedOn}, y el historial de
 * afiliaciones cerradas se conserva (D32, V9).</p>
 *
 * <p>{@code id} es nulo mientras la afiliacion no se ha persistido. El numero de afiliado no se pide
 * en ninguna ruta, por eso no viaja en el modelo: la fila lo deja nulo.</p>
 */
public record Affiliation(Long id, long userId, int epsPlanId, boolean current, LocalDate startedOn,
        LocalDate endedOn) {

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
        // Mismo invariante que ck_affiliations_dates (V2).
        if (endedOn != null && endedOn.isBefore(startedOn)) {
            throw new IllegalArgumentException("Una afiliacion no puede terminar antes de empezar");
        }
    }

    /** Afiliacion vigente que empieza hoy: la crean el registro (HU-009) y el perfil (D26). */
    public static Affiliation startingToday(long userId, int epsPlanId, LocalDate today) {
        return new Affiliation(null, userId, epsPlanId, true, today, null);
    }

    public Affiliation withId(long assignedId) {
        return new Affiliation(assignedId, userId, epsPlanId, current, startedOn, endedOn);
    }

    /** ¿Es una afiliacion a este plan? "Mismo plan = sin cambios" (contrato S4). */
    public boolean isFor(int planId) {
        return epsPlanId == planId;
    }

    /**
     * D26: deja de estar vigente el dia {@code today} (cambio de plan, HU-009 CA-09, o baja, CA-10).
     * Solo se cierra una vigente; cerrarla el mismo dia en que empezo es valido.
     */
    public Affiliation close(LocalDate today) {
        if (!current) {
            throw new IllegalStateException("La afiliacion ya no esta vigente");
        }
        return new Affiliation(id, userId, epsPlanId, false, startedOn, today);
    }
}
