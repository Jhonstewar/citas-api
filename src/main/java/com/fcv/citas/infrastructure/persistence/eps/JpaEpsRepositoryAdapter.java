package com.fcv.citas.infrastructure.persistence.eps;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.eps.Eps;
import com.fcv.citas.domain.eps.EpsRepository;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.NotFoundException;

/** Adaptador JPA de {@link EpsRepository}. */
@Component
class JpaEpsRepositoryAdapter implements EpsRepository {

    private final SpringDataEpsRepository eps;
    private final JdbcTemplate jdbc;

    JpaEpsRepositoryAdapter(SpringDataEpsRepository eps, JdbcTemplate jdbc) {
        this.eps = eps;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Eps> findById(int id) {
        return eps.findById(id).map(JpaEpsRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return count("SELECT COUNT(*) FROM eps WHERE code = ?", code) > 0;
    }

    /** Con la collation de la columna (V9): la misma regla que {@code uq_eps_name}. */
    @Override
    public boolean existsByName(String name, Integer excludingId) {
        return excludingId == null ? count("SELECT COUNT(*) FROM eps WHERE name = ?", name) > 0
                : count("SELECT COUNT(*) FROM eps WHERE name = ? AND id <> ?", name, excludingId) > 0;
    }

    @Override
    public Eps save(Eps domain) {
        EpsJpaEntity entity;
        if (domain.id() == null) {
            entity = new EpsJpaEntity(domain.code(), domain.name(), domain.active());
        } else {
            entity = eps.findById(domain.id()).orElseThrow(() -> new NotFoundException("La EPS no existe"));
            entity.update(domain.name(), domain.active());
        }
        try {
            return toDomain(eps.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Carrera entre existsBy* y la escritura: deciden uq_eps_code (V2) y uq_eps_name (V9).
            String cause = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (cause.contains("uq_eps_name")) {
                throw new DuplicateValueException("name", "Ya existe una EPS con ese nombre");
            }
            throw new DuplicateValueException("code", "Ya existe una EPS con ese código");
        }
    }

    @Override
    public boolean hasPlans(int id) {
        return count("SELECT COUNT(*) FROM eps_plans WHERE eps_id = ?", id) > 0;
    }

    @Override
    public void delete(int id) {
        eps.deleteById(id);
        eps.flush();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static Eps toDomain(EpsJpaEntity e) {
        return new Eps(e.getId(), e.getCode(), e.getName(), e.isActive());
    }
}
