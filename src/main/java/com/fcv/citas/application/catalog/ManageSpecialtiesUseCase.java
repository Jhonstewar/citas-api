package com.fcv.citas.application.catalog;

import java.util.List;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.domain.catalog.Specialty;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.NotFoundException;

/** HU-011: CRUD de especialidades para ADMIN, sin borrado fisico de lo referenciado (RF-06). */
public class ManageSpecialtiesUseCase {

    private final SpecialtyRepository specialties;
    private final TransactionRunner tx;

    public ManageSpecialtiesUseCase(SpecialtyRepository specialties, TransactionRunner tx) {
        this.specialties = specialties;
        this.tx = tx;
    }

    public List<Specialty> listAll() {
        return specialties.findAll();
    }

    /** Solo las activas: lo que se ofrece para reservar (HU-011 CA-06). */
    public List<Specialty> listActive() {
        return specialties.findAllActive();
    }

    public Specialty create(String code, String name, AppointmentType type, int durationMinutes) {
        Specialty specialty = Specialty.create(code, name, type, durationMinutes);
        return tx.inTransaction(() -> {
            if (specialties.existsByCode(specialty.code())) {
                throw new DuplicateValueException("code", "Ya existe una especialidad con ese código");
            }
            return specialties.save(specialty);
        });
    }

    public Specialty update(int id, String name, AppointmentType type, int durationMinutes) {
        return tx.inTransaction(() -> specialties.save(get(id).withDetails(name, type, durationMinutes)));
    }

    public Specialty setActive(int id, boolean active) {
        return tx.inTransaction(() -> specialties.save(get(id).withActive(active)));
    }

    public void delete(int id) {
        tx.inTransaction(() -> {
            Specialty specialty = get(id);
            if (specialty.isProtected() || specialties.isReferenced(id)) {
                throw new ConflictException("SPECIALTY_REFERENCED",
                        "La especialidad está en uso por profesionales o citas: desactívela en lugar de borrarla");
            }
            specialties.delete(id);
            return null;
        });
    }

    private Specialty get(int id) {
        return specialties.findById(id).orElseThrow(() -> new NotFoundException("La especialidad no existe"));
    }
}
