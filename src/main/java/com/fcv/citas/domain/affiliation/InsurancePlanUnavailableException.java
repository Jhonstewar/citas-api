package com.fcv.citas.domain.affiliation;

import com.fcv.citas.domain.shared.BusinessRuleException;

/**
 * El plan de EPS elegido no es seleccionable: no existe, esta desactivado o pertenece a una EPS
 * desactivada (RF-04, RF-06). Los tres casos comparten codigo y mensaje a proposito: la respuesta
 * no revela si un plan existe.
 */
public class InsurancePlanUnavailableException extends BusinessRuleException {

    public InsurancePlanUnavailableException() {
        super("INSURANCE_PLAN_UNAVAILABLE", "El plan de EPS seleccionado no está disponible");
    }
}
