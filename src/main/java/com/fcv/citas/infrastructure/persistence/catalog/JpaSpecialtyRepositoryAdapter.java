package com.fcv.citas.infrastructure.persistence.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.domain.catalog.Specialty;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.NotFoundException;

/** Adaptador JPA de {@link SpecialtyRepository}. */
@Component
class JpaSpecialtyRepositoryAdapter implements SpecialtyRepository {

    private final SpringDataSpecialtyRepository specialties;
    private final SpringDataAppointmentTypeRepository types;
    private final JdbcTemplate jdbc;

    JpaSpecialtyRepositoryAdapter(SpringDataSpecialtyRepository specialties,
            SpringDataAppointmentTypeRepository types, JdbcTemplate jdbc) {
        this.specialties = specialties;
        this.types = types;
        this.jdbc = jdbc;
    }

    @Override
    public List<Specialty> findAll() {
        return specialties.findAllByOrderByNameAsc().stream().map(JpaSpecialtyRepositoryAdapter::toDomain).toList();
    }

    @Override
    public List<Specialty> findAllActive() {
        return specialties.findByActiveTrueOrderByNameAsc().stream().map(JpaSpecialtyRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<Specialty> findById(int id) {
        return specialties.findById(id).map(JpaSpecialtyRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return specialties.existsByCode(code);
    }

    @Override
    public boolean existsByName(String name, Integer excludingId) {
        return excludingId == null ? specialties.existsByNameIgnoreCase(name)
                : specialties.existsByNameIgnoreCaseAndIdNot(name, excludingId);
    }

    @Override
    public Specialty save(Specialty specialty) {
        AppointmentTypeJpaEntity type = types.findByCode(specialty.appointmentType().name())
                .orElseThrow(() -> new IllegalStateException("Catalogo appointment_types incompleto (V4)"));
        SpecialtyJpaEntity entity;
        if (specialty.id() == null) {
            entity = new SpecialtyJpaEntity(type, specialty.code(), specialty.name(),
                    (short) specialty.durationMinutes(), specialty.active());
        } else {
            entity = specialties.findById(specialty.id())
                    .orElseThrow(() -> new NotFoundException("La especialidad no existe"));
            entity.update(type, specialty.name(), (short) specialty.durationMinutes(), specialty.active());
        }
        try {
            return toDomain(specialties.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Carrera entre existsByCode y el INSERT: la restriccion uq_specialties_code decide.
            throw new DuplicateValueException("code", "Ya existe una especialidad con ese código");
        }
    }

    @Override
    public boolean isReferenced(int id) {
        Integer uses = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM professional_specialties WHERE specialty_id = ?)"
                        + " + (SELECT COUNT(*) FROM appointments WHERE specialty_id = ?)",
                Integer.class, id, id);
        return uses != null && uses > 0;
    }

    @Override
    public void delete(int id) {
        specialties.deleteById(id);
        specialties.flush();
    }

    static Specialty toDomain(SpecialtyJpaEntity e) {
        return new Specialty(e.getId(), e.getCode(), e.getName(),
                AppointmentType.valueOf(e.getAppointmentType().getCode()), e.getDurationMinutes(), e.isActive());
    }
}
