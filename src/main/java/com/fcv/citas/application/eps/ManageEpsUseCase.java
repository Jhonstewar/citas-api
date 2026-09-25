package com.fcv.citas.application.eps;

import java.util.List;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.eps.EpsQueries.EpsPlanView;
import com.fcv.citas.application.eps.EpsQueries.EpsView;
import com.fcv.citas.domain.eps.Eps;
import com.fcv.citas.domain.eps.EpsPlan;
import com.fcv.citas.domain.eps.EpsPlanRepository;
import com.fcv.citas.domain.eps.EpsRepository;
import com.fcv.citas.domain.eps.RegimeCatalog;
import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;

/**
 * HU-012: CRUD de EPS y planes para ADMIN (RF-06). Mismo comportamiento que especialidades
 * (HU-011): nombres y codigos unicos (409 {@code DUPLICATE} con el campo; D33), activar y
 * desactivar como operaciones del dominio, y borrado fisico solo de lo que nada referencia (D28):
 * una EPS con planes → 409 {@code EPS_REFERENCED}; un plan con afiliaciones, vigentes o cerradas → 409
 * {@code PLAN_REFERENCED}. Desactivar no toca las afiliaciones existentes (CA-05).
 */
public class ManageEpsUseCase {

    static final String EPS_NOT_FOUND = "La EPS no existe";
    static final String PLAN_NOT_FOUND = "El plan no existe";

    private final EpsRepository eps;
    private final EpsPlanRepository plans;
    private final RegimeCatalog regimes;
    private final EpsQueries queries;
    private final TransactionRunner tx;

    public ManageEpsUseCase(EpsRepository eps, EpsPlanRepository plans, RegimeCatalog regimes, EpsQueries queries,
            TransactionRunner tx) {
        this.eps = eps;
        this.plans = plans;
        this.regimes = regimes;
        this.queries = queries;
        this.tx = tx;
    }

    // ------------------------------------------------------------------ EPS

    public List<EpsView> list() {
        return queries.findAll();
    }

    public EpsView get(int id) {
        return queries.findById(id).orElseThrow(() -> new NotFoundException(EPS_NOT_FOUND));
    }

    public EpsView create(String code, String name) {
        Eps created = Eps.create(code, name);
        int id = tx.inTransaction(() -> {
            if (eps.existsByCode(created.code())) {
                throw new DuplicateValueException("code", "Ya existe una EPS con ese código");
            }
            requireUniqueEpsName(created.name(), null);
            return eps.save(created).id();
        });
        return get(id);
    }

    /** Contrato S4: de la EPS solo se edita el nombre; el codigo es estable. */
    public EpsView rename(int id, String name) {
        tx.inTransaction(() -> {
            Eps renamed = epsOf(id).rename(name);
            requireUniqueEpsName(renamed.name(), id);
            return eps.save(renamed);
        });
        return get(id);
    }

    /** CA-05 / CA-06: desactivarla la retira de la oferta publica; reactivarla la devuelve. */
    public EpsView setActive(int id, boolean active) {
        tx.inTransaction(() -> {
            Eps current = epsOf(id);
            return eps.save(active ? current.activate() : current.deactivate());
        });
        return get(id);
    }

    /** D28 / CA-09: solo sin planes (y por tanto sin afiliaciones). */
    public void delete(int id) {
        tx.inTransaction(() -> {
            epsOf(id);
            if (eps.hasPlans(id)) {
                throw new ConflictException("EPS_REFERENCED",
                        "La EPS tiene planes registrados: desactívela en lugar de borrarla");
            }
            eps.delete(id);
            return null;
        });
    }

    // ------------------------------------------------------------------ planes

    public List<EpsPlanView> plans(int epsId) {
        get(epsId);
        return queries.plansOf(epsId);
    }

    /** CA-01 / CA-02: la EPS es la de la ruta (404 si no existe) y el regimen, del catalogo fijo (400). */
    public EpsPlanView createPlan(int epsId, String code, String name, String regimeCode) {
        EpsPlan created = EpsPlan.create(epsId, code, name, regimeCode);
        int id = tx.inTransaction(() -> {
            epsOf(epsId);
            requireRegime(created.regimeCode());
            if (plans.existsByCode(epsId, created.code())) {
                throw new DuplicateValueException("code", "Ya existe un plan con ese código en esta EPS");
            }
            requireUniquePlanName(epsId, created.name(), null);
            return plans.save(created).id();
        });
        return plan(id);
    }

    /** Contrato S4: nombre y regimen; el codigo y la EPS del plan no cambian. */
    public EpsPlanView updatePlan(int id, String name, String regimeCode) {
        tx.inTransaction(() -> {
            EpsPlan updated = planOf(id).withDetails(name, regimeCode);
            requireRegime(updated.regimeCode());
            requireUniquePlanName(updated.epsId(), updated.name(), id);
            return plans.save(updated);
        });
        return plan(id);
    }

    /** CA-04 / CA-06: un plan inactivo no se ofrece ni se acepta en una afiliacion nueva. */
    public EpsPlanView setPlanActive(int id, boolean active) {
        tx.inTransaction(() -> {
            EpsPlan current = planOf(id);
            return plans.save(active ? current.activate() : current.deactivate());
        });
        return plan(id);
    }

    /** D28 / CA-03 / CA-09: solo si ninguna afiliacion (vigente o cerrada) lo referencia. */
    public void deletePlan(int id) {
        tx.inTransaction(() -> {
            planOf(id);
            if (plans.isReferenced(id)) {
                throw new ConflictException("PLAN_REFERENCED",
                        "El plan está referenciado por afiliaciones: desactívelo en lugar de borrarlo");
            }
            plans.delete(id);
            return null;
        });
    }

    // ------------------------------------------------------------------ reglas

    private void requireUniqueEpsName(String name, Integer excludingId) {
        if (eps.existsByName(name, excludingId)) {
            throw new DuplicateValueException("name", "Ya existe una EPS con ese nombre");
        }
    }

    private void requireUniquePlanName(int epsId, String name, Integer excludingId) {
        if (plans.existsByName(epsId, name, excludingId)) {
            throw new DuplicateValueException("name", "Ya existe un plan con ese nombre en esta EPS");
        }
    }

    private void requireRegime(String regimeCode) {
        if (!regimes.exists(regimeCode)) {
            throw InvalidRequestException.field("regimeCode", "El régimen no existe en el catálogo");
        }
    }

    private Eps epsOf(int id) {
        return eps.findById(id).orElseThrow(() -> new NotFoundException(EPS_NOT_FOUND));
    }

    private EpsPlan planOf(int id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException(PLAN_NOT_FOUND));
    }

    private EpsPlanView plan(int id) {
        return queries.findPlan(id).orElseThrow(() -> new NotFoundException(PLAN_NOT_FOUND));
    }
}
