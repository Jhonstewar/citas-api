package com.fcv.citas.infrastructure.rest.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fcv.citas.infrastructure.rest.validation.PasswordPolicyCompliant;

/**
 * Cuerpo de {@code POST /api/auth/register}. Nombres canonicos alineados con
 * {@code citas-web/src/api/contracts.ts} y con el esquema ({@code first_names}, {@code last_names});
 * se aceptan como alias {@code firstName}, {@code lastName} y {@code documentTypeCode}.
 */
public record RegisterRequest(
        @JsonAlias("firstName") @NotBlank @Size(max = 100) String firstNames,
        @JsonAlias("lastName") @NotBlank @Size(max = 100) String lastNames,
        @JsonAlias("documentTypeCode") @NotBlank @Size(max = 10) String documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 30) String phone,
        // Politica D29: minimo 8, letra y numero, maximo 72 BYTES UTF-8 (limite de BCrypt; @Size
        // contaba caracteres y 40 x ñ, 80 bytes, llegaba al hasher y acababa en 500).
        @NotBlank @PasswordPolicyCompliant String password,
        // HU-009: afiliacion OPCIONAL. Ausente o nulo = registro sin afiliacion. Que el plan exista
        // y sea seleccionable no se puede decidir aqui: depende del catalogo y lo valida el caso
        // de uso, que responde 422 INSURANCE_PLAN_UNAVAILABLE.
        Integer insurancePlanId) {

    @Override
    public String toString() {
        return "RegisterRequest[documentType=%s, password=***]".formatted(documentType);
    }
}
