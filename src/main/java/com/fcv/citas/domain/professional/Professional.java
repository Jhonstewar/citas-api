package com.fcv.citas.domain.professional;

import java.util.Objects;
import java.util.Set;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Perfil profesional (RF-07), 1:1 con un usuario de rol PROFESSIONAL. Codigo y matricula son
 * sinteticos y unicos; no se editan tras el alta (HU-013, INC-015).
 */
public record Professional(
        Long id,
        long userId,
        String professionalCode,
        String licenseNumber,
        boolean active,
        SpecialtyAssignment specialties,
        Set<Integer> siteIds) {

    public Professional {
        if (professionalCode == null || professionalCode.isBlank()) {
            throw InvalidRequestException.field("professionalCode", "El código profesional es obligatorio");
        }
        if (licenseNumber == null || licenseNumber.isBlank()) {
            throw InvalidRequestException.field("licenseNumber", "La matrícula es obligatoria");
        }
        professionalCode = professionalCode.trim().toUpperCase(java.util.Locale.ROOT);
        licenseNumber = licenseNumber.trim().toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(specialties, "specialties");
        siteIds = requireSites(siteIds);
    }

    /** HU-015: al menos una sede (CA-02). */
    public static Set<Integer> requireSites(Set<Integer> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            throw InvalidRequestException.field("siteIds", "Asigne al menos una sede");
        }
        // No `contains(null)`: sobre un Set inmutable (Set.copyOf) lanza NPE en vez de dar false.
        if (siteIds.stream().anyMatch(Objects::isNull)) {
            throw InvalidRequestException.field("siteIds", "Hay una sede sin identificador");
        }
        return Set.copyOf(siteIds);
    }

    /**
     * HU-016 CA-02: vuelve a ofrecerse con sus especialidades y sedes previas. Idempotente.
     */
    public Professional activate() {
        return active ? this : withActive(true);
    }

    /**
     * HU-016 CA-01 y CA-05: deja de ofrecerse para nuevas reservas (la busqueda y la reserva leen
     * {@code active}), pero no toca nada mas: sus citas, reservas de slot e historial se conservan
     * (D11). No existe borrado fisico (CA-06). Idempotente.
     */
    public Professional deactivate() {
        return active ? withActive(false) : this;
    }

    private Professional withActive(boolean value) {
        return new Professional(id, userId, professionalCode, licenseNumber, value, specialties, siteIds);
    }

    public boolean worksAt(int siteId) {
        return siteIds.contains(siteId);
    }

    public boolean offers(int specialtyId) {
        return specialties.specialtyIds().contains(specialtyId);
    }
}
