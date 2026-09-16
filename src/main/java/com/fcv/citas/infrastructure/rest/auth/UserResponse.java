package com.fcv.citas.infrastructure.rest.auth;

import java.util.Collection;
import java.util.List;

import com.fcv.citas.domain.user.User;

/** Datos basicos de un usuario. Nunca incluye la contraseña ni su hash. */
public record UserResponse(
        Long id,
        String firstNames,
        String lastNames,
        String documentType,
        String documentNumber,
        String email,
        String phone,
        List<String> roles) {

    public static UserResponse from(User user, Collection<String> roles) {
        return new UserResponse(user.id(), user.firstNames(), user.lastNames(), user.documentTypeCode(),
                user.documentNumber(), user.email(), user.phone(), roles.stream().sorted().toList());
    }

    public static UserResponse from(User user) {
        return from(user, user.roles().stream().map(Enum::name).toList());
    }
}
