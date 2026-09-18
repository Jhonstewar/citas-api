package com.fcv.citas.domain.professional;

import java.util.Optional;
import java.util.Set;

/** Puerto de salida de los perfiles profesionales (RF-07). No existe borrado (HU-016 CA-06). */
public interface ProfessionalRepository {

    boolean existsByCode(String professionalCode);

    boolean existsByLicense(String licenseNumber);

    /**
     * Inserta el perfil con sus especialidades y sedes.
     *
     * @throws com.fcv.citas.domain.shared.DuplicateValueException si la BD rechaza codigo o
     *         matricula por unicidad (carrera con otra alta)
     */
    Professional saveNew(Professional professional);

    Optional<Professional> findById(long id);

    Optional<Professional> findByUserId(long userId);

    void replaceSpecialties(long professionalId, SpecialtyAssignment assignment);

    void replaceSites(long professionalId, Set<Integer> siteIds);

    void setActive(long professionalId, boolean active);
}
