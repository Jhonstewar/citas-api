package com.fcv.citas.domain.professional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Conjunto de especialidades de un profesional con exactamente una primaria (RF-07, HU-014).
 * La validez de cada especialidad (existe y esta activa) la comprueba el caso de uso contra el
 * catalogo; aqui solo la forma del conjunto.
 */
public record SpecialtyAssignment(Set<Integer> specialtyIds, int primarySpecialtyId) {

    public SpecialtyAssignment {
        specialtyIds = Set.copyOf(specialtyIds);
    }

    /**
     * @throws InvalidRequestException si el conjunto esta vacio, repite especialidades, no tiene
     *                                 primaria o la primaria no pertenece al conjunto (CA-02, CA-03)
     */
    public static SpecialtyAssignment of(List<Integer> ids, Integer primaryId) {
        if (ids == null || ids.isEmpty()) {
            throw InvalidRequestException.field("specialtyIds", "Asigne al menos una especialidad");
        }
        Set<Integer> unique = new LinkedHashSet<>(ids);
        if (unique.contains(null)) {
            throw InvalidRequestException.field("specialtyIds", "Hay una especialidad sin identificador");
        }
        if (unique.size() != ids.size()) {
            throw InvalidRequestException.field("specialtyIds", "Hay especialidades repetidas");
        }
        if (primaryId == null) {
            throw InvalidRequestException.field("primarySpecialtyId", "Marque una especialidad como primaria");
        }
        if (!unique.contains(primaryId)) {
            throw InvalidRequestException.field("primarySpecialtyId",
                    "La especialidad primaria debe estar entre las asignadas");
        }
        return new SpecialtyAssignment(unique, primaryId);
    }
}
