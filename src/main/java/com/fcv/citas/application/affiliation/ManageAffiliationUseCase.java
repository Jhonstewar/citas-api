package com.fcv.citas.application.affiliation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.affiliation.AffiliationQueries.AffiliationView;
import com.fcv.citas.domain.affiliation.Affiliation;
import com.fcv.citas.domain.affiliation.AffiliationRepository;
import com.fcv.citas.domain.affiliation.InsurancePlanCatalog;
import com.fcv.citas.domain.affiliation.InsurancePlanUnavailableException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-009, segundo corte: el paciente fija, cambia o quita su afiliacion desde el perfil (D26).
 *
 * <p>El titular es SIEMPRE el usuario del token: ninguna operacion recibe el id de otra persona, asi
 * que no existe forma de leer ni tocar la afiliacion de otro (CA-07, HU-005). Cambiar de plan cierra
 * la vigente ({@code ended_on} = hoy en America/Bogota) y abre la nueva en UNA transaccion: si el alta
 * falla, la anterior sigue vigente. El plan nuevo pasa por el mismo predicado "ofrecible" que el
 * registro y el catalogo publico ({@link InsurancePlanCatalog}), asi que no pueden divergir.</p>
 */
public class ManageAffiliationUseCase {

    private final AffiliationRepository affiliations;
    private final InsurancePlanCatalog plans;
    private final AffiliationQueries queries;
    private final TransactionRunner tx;
    private final Clock clock;

    public ManageAffiliationUseCase(AffiliationRepository affiliations, InsurancePlanCatalog plans,
            AffiliationQueries queries, TransactionRunner tx, Clock clock) {
        this.affiliations = affiliations;
        this.plans = plans;
        this.queries = queries;
        this.tx = tx;
        this.clock = clock;
    }

    public Optional<AffiliationView> current(long userId) {
        return queries.current(userId);
    }

    /**
     * CA-01 / CA-09 (D26): fija o cambia el plan vigente. El mismo plan que ya esta vigente no cambia
     * nada y devuelve la afiliacion actual (aclaracion del contrato S4). Un plan no ofrecible → 422
     * {@code INSURANCE_PLAN_UNAVAILABLE} y la afiliacion vigente queda intacta (CA-06).
     */
    public AffiliationView change(long userId, int insurancePlanId) {
        long id = tx.inTransaction(() -> {
            Optional<Affiliation> current = affiliations.lockCurrent(userId);
            if (current.isPresent() && current.get().isFor(insurancePlanId)) {
                return current.get().id();
            }
            if (!plans.isSelectable(insurancePlanId)) {
                throw new InsurancePlanUnavailableException();
            }
            LocalDate today = SystemZone.today(clock);
            current.ifPresent(c -> affiliations.close(c.close(today)));
            return affiliations.saveNew(Affiliation.startingToday(userId, insurancePlanId, today)).id();
        });
        return queries.findById(id).orElseThrow(() -> new NotFoundException("La afiliación no existe"));
    }

    /** CA-10 (D26): la cierra sin reemplazo; sin afiliacion vigente no hay nada que hacer. */
    public void remove(long userId) {
        tx.inTransaction(() -> {
            affiliations.lockCurrent(userId).ifPresent(c -> affiliations.close(c.close(SystemZone.today(clock))));
            return null;
        });
    }
}
