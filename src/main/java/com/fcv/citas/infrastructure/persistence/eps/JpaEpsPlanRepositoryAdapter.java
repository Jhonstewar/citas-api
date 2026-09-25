package com.fcv.citas.infrastructure.persistence.eps;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.eps.EpsPlan;
import com.fcv.citas.domain.eps.EpsPlanRepository;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.NotFoundException;

/** Adaptador JPA de {@link EpsPlanRepository}. El regimen se traduce entre codigo (dominio) e id (fila). */
@Component
class JpaEpsPlanRepositoryAdapter implements EpsPlanRepository {

    private final SpringDataEpsPlanRepository plans;
    private final JdbcTemplate jdbc;

    JpaEpsPlanRepositoryAdapter(SpringDataEpsPlanRepository plans, JdbcTemplate jdbc) {
        this.plans = plans;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EpsPlan> findById(int id) {
        return plans.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(int epsId, String code) {
        return count("SELECT COUNT(*) FROM eps_plans WHERE eps_id = ? AND code = ?", epsId, code) > 0;
    }

    /** Con la collation de la columna (V9): la misma regla que {@code uq_eps_plans_eps_name}. */
    @Override
    public boolean existsByName(int epsId, String name, Integer excludingId) {
        return excludingId == null
                ? count("SELECT COUNT(*) FROM eps_plans WHERE eps_id = ? AND name = ?", epsId, name) > 0
                : count("SELECT COUNT(*) FROM eps_plans WHERE eps_id = ? AND name = ? AND id <> ?", epsId, name,
                        excludingId) > 0;
    }

    @Override
    public EpsPlan save(EpsPlan domain) {
        short regimeId = regimeId(domain.regimeCode());
        EpsPlanJpaEntity entity;
        if (domain.id() == null) {
            entity = new EpsPlanJpaEntity(domain.epsId(), regimeId, domain.code(), domain.name(), domain.active());
        } else {
            entity = plans.findById(domain.id()).orElseThrow(() -> new NotFoundException("El plan no existe"));
            entity.update(regimeId, domain.name(), domain.active());
        }
        try {
            return toDomain(plans.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Carrera: deciden uq_eps_plans_eps_code (V2) y uq_eps_plans_eps_name (V9).
            String cause = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (cause.contains("uq_eps_plans_eps_name")) {
                throw new DuplicateValueException("name", "Ya existe un plan con ese nombre en esta EPS");
            }
            throw new DuplicateValueException("code", "Ya existe un plan con ese código en esta EPS");
        }
    }

    @Override
    public boolean isReferenced(int id) {
        return count("SELECT COUNT(*) FROM affiliations WHERE eps_plan_id = ?", id) > 0;
    }

    @Override
    public void delete(int id) {
        plans.deleteById(id);
        plans.flush();
    }

    private short regimeId(String code) {
        return jdbc.queryForObject("SELECT id FROM regimes WHERE code = ?", Short.class, code);
    }

    private EpsPlan toDomain(EpsPlanJpaEntity e) {
        String regime = jdbc.queryForObject("SELECT code FROM regimes WHERE id = ?", String.class, e.getRegimeId());
        return new EpsPlan(e.getId(), e.getEpsId(), e.getCode(), e.getName(), regime, e.isActive());
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
