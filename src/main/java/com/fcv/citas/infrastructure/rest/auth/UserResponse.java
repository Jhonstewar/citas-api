package com.fcv.citas.infrastructure.rest.auth;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fcv.citas.application.affiliation.AffiliationQueries.AffiliationView;
import com.fcv.citas.application.catalog.CatalogQueries.InsurancePlanView;
import com.fcv.citas.domain.user.User;

/**
 * Datos basicos de un usuario. Nunca incluye la contraseña ni su hash. {@code affiliation} es aditivo
 * (contrato S4 de identidad): solo lo llevan {@code GET/PUT /api/me}, y se omite si es nulo (sin
 * afiliacion vigente, o respuestas como la del registro, cuya forma no cambia).
 */
public record UserResponse(
        Long id,
        String firstNames,
        String lastNames,
        String documentType,
        String documentNumber,
        String email,
        String phone,
        List<String> roles,
        AffiliationResponse affiliation) {

    /** {@code Affiliation} del contrato: {@code plan} con el mismo cuerpo que el catalogo publico. */
    public record AffiliationResponse(long id, InsurancePlanView plan, LocalDate startedOn) {

        public static AffiliationResponse from(AffiliationView a) {
            return new AffiliationResponse(a.id(), a.plan(), a.startedOn());
        }
    }

    public static UserResponse from(User user, Collection<String> roles) {
        return from(user, roles, Optional.empty());
    }

    public static UserResponse from(User user, Collection<String> roles, Optional<AffiliationView> affiliation) {
        return new UserResponse(user.id(), user.firstNames(), user.lastNames(), user.documentTypeCode(),
                user.documentNumber(), user.email(), user.phone(), roles.stream().sorted().toList(),
                affiliation.map(AffiliationResponse::from).orElse(null));
    }

    public static UserResponse from(User user) {
        return from(user, user.roles().stream().map(Enum::name).toList());
    }
}
