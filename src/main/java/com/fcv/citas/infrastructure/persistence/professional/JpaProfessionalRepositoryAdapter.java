package com.fcv.citas.infrastructure.persistence.professional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.professional.Professional;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.professional.SpecialtyAssignment;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.NotFoundException;

/** Adaptador JPA de {@link ProfessionalRepository}: perfil, especialidades y sedes. */
@Component
class JpaProfessionalRepositoryAdapter implements ProfessionalRepository {

    private final SpringDataProfessionalRepository professionals;
    private final SpringDataProfessionalSpecialtyRepository specialties;
    private final SpringDataProfessionalSiteRepository sites;

    JpaProfessionalRepositoryAdapter(SpringDataProfessionalRepository professionals,
            SpringDataProfessionalSpecialtyRepository specialties, SpringDataProfessionalSiteRepository sites) {
        this.professionals = professionals;
        this.specialties = specialties;
        this.sites = sites;
    }

    @Override
    public boolean existsByCode(String professionalCode) {
        return professionals.existsByProfessionalCode(professionalCode);
    }

    @Override
    public boolean existsByLicense(String licenseNumber) {
        return professionals.existsByLicenseNumber(licenseNumber);
    }

    @Override
    public Professional saveNew(Professional p) {
        ProfessionalJpaEntity saved;
        try {
            saved = professionals.saveAndFlush(
                    new ProfessionalJpaEntity(p.userId(), p.professionalCode(), p.licenseNumber(), p.active()));
        } catch (DataIntegrityViolationException e) {
            // Carrera con otra alta: la restriccion unica decide (HU-013 CA-03).
            String cause = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (cause.contains("uq_professionals_license")) {
                throw new DuplicateValueException("licenseNumber", "Ya existe un profesional con esa matrícula");
            }
            throw new DuplicateValueException("professionalCode", "Ya existe un profesional con ese código");
        }
        insertSpecialties(saved.getId(), p.specialties());
        insertSites(saved.getId(), p.siteIds());
        return toDomain(saved);
    }

    @Override
    public Optional<Professional> findById(long id) {
        return professionals.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Professional> findByUserId(long userId) {
        return professionals.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public void replaceSpecialties(long professionalId, SpecialtyAssignment assignment) {
        specialties.deleteAllOfProfessional(professionalId);
        insertSpecialties(professionalId, assignment);
    }

    @Override
    public void replaceSites(long professionalId, Set<Integer> siteIds) {
        sites.deleteAllOfProfessional(professionalId);
        insertSites(professionalId, siteIds);
    }

    @Override
    public void setActive(long professionalId, boolean active) {
        ProfessionalJpaEntity entity = professionals.findById(professionalId)
                .orElseThrow(() -> new NotFoundException("El profesional no existe"));
        entity.setActive(active);
        professionals.saveAndFlush(entity);
    }

    private void insertSpecialties(long professionalId, SpecialtyAssignment assignment) {
        specialties.saveAllAndFlush(assignment.specialtyIds().stream()
                .map(id -> new ProfessionalSpecialtyJpaEntity(professionalId, id,
                        id == assignment.primarySpecialtyId()))
                .toList());
    }

    private void insertSites(long professionalId, Set<Integer> siteIds) {
        sites.saveAllAndFlush(siteIds.stream().map(id -> new ProfessionalSiteJpaEntity(professionalId, id)).toList());
    }

    private Professional toDomain(ProfessionalJpaEntity e) {
        List<ProfessionalSpecialtyJpaEntity> assigned = specialties.findByIdProfessionalId(e.getId());
        int primary = assigned.stream().filter(ProfessionalSpecialtyJpaEntity::isPrimary)
                .map(s -> s.getId().specialtyId()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Profesional sin especialidad primaria: " + e.getId()));
        SpecialtyAssignment assignment = new SpecialtyAssignment(
                assigned.stream().map(s -> s.getId().specialtyId()).collect(Collectors.toSet()), primary);
        Set<Integer> siteIds = sites.findByIdProfessionalId(e.getId()).stream()
                .map(s -> (int) s.getId().siteId()).collect(Collectors.toSet());
        return new Professional(e.getId(), e.getUserId(), e.getProfessionalCode(), e.getLicenseNumber(),
                e.isActive(), assignment, siteIds);
    }
}
