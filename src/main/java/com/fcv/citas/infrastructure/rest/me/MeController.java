package com.fcv.citas.infrastructure.rest.me;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fcv.citas.application.affiliation.ManageAffiliationUseCase;
import com.fcv.citas.application.user.ProfileUseCase;
import com.fcv.citas.application.user.ProfileUseCase.Profile;
import com.fcv.citas.infrastructure.rest.CurrentUser;
import com.fcv.citas.infrastructure.rest.auth.UserResponse;
import com.fcv.citas.infrastructure.rest.auth.UserResponse.AffiliationResponse;

/**
 * Perfil del usuario autenticado: {@code GET/PUT /api/me} (HU-008) y su afiliacion
 * {@code PUT/DELETE /api/me/affiliation} (HU-009, solo USER por {@code SecurityConfig}). Identidad y
 * roles salen de {@link CurrentUser} (R3 de S4): no hay ruta que reciba el id de otra persona.
 */
@RestController
class MeController {

    /**
     * Cuerpo de {@code PUT /api/me}. Los editables se validan con los mismos limites que el registro
     * (CA-06). Cualquier OTRO campo presente —aunque sea nulo— se recoge por nombre, sin guardar su
     * valor (p. ej. una contraseña), para que el dominio decida si es un campo fijo (D25).
     */
    static final class UpdateProfileRequest {

        @JsonProperty
        @NotBlank
        @Size(max = 100)
        private String firstNames;

        @JsonProperty
        @NotBlank
        @Size(max = 100)
        private String lastNames;

        @JsonProperty
        @NotBlank
        @Size(max = 30)
        private String phone;

        private final Set<String> otherFields = new LinkedHashSet<>();

        @JsonAnySetter
        void otherField(String name, Object ignoredValue) {
            otherFields.add(name);
        }

        @Override
        public String toString() {
            return "UpdateProfileRequest[otherFields=%s]".formatted(otherFields);
        }
    }

    record AffiliationRequest(@NotNull(message = "Seleccione el plan de EPS") Integer insurancePlanId) {
    }

    private final ProfileUseCase profiles;
    private final ManageAffiliationUseCase affiliations;

    MeController(ProfileUseCase profiles, ManageAffiliationUseCase affiliations) {
        this.profiles = profiles;
        this.affiliations = affiliations;
    }

    @GetMapping("/api/me")
    UserResponse me(JwtAuthenticationToken auth) {
        return response(profiles.get(CurrentUser.id(auth)), auth);
    }

    /** HU-008: 200 {@code UserResponse} · 400 VALIDATION · 400 FIELD_NOT_EDITABLE con {@code field}. */
    @PutMapping("/api/me")
    UserResponse update(JwtAuthenticationToken auth, @Valid @RequestBody UpdateProfileRequest request) {
        return response(profiles.update(CurrentUser.id(auth), request.otherFields, request.firstNames,
                request.lastNames, request.phone), auth);
    }

    /** HU-009 (D26): 200 {@code Affiliation} · 422 INSURANCE_PLAN_UNAVAILABLE. Mismo plan = sin cambios. */
    @PutMapping("/api/me/affiliation")
    AffiliationResponse setAffiliation(JwtAuthenticationToken auth, @Valid @RequestBody AffiliationRequest request) {
        return AffiliationResponse.from(affiliations.change(CurrentUser.id(auth), request.insurancePlanId()));
    }

    /** HU-009 CA-10: 204, haya o no afiliacion vigente. */
    @DeleteMapping("/api/me/affiliation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeAffiliation(JwtAuthenticationToken auth) {
        affiliations.remove(CurrentUser.id(auth));
    }

    private static UserResponse response(Profile profile, JwtAuthenticationToken auth) {
        return UserResponse.from(profile.user(), CurrentUser.roles(auth), profile.affiliation());
    }
}
