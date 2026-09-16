package com.fcv.citas.domain.user;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Identidad de una persona del sistema. {@code id} es nulo mientras no se ha persistido.
 * {@code passwordHash} nunca contiene la clave en claro y no forma parte de {@link #toString()}.
 */
public record User(
        Long id,
        String documentTypeCode,
        String documentNumber,
        String firstNames,
        String lastNames,
        String email,
        String phone,
        String passwordHash,
        boolean active,
        Set<Role> roles) {

    public User {
        requireText(documentTypeCode, "documentTypeCode");
        requireText(documentNumber, "documentNumber");
        requireText(firstNames, "firstNames");
        requireText(lastNames, "lastNames");
        requireText(email, "email");
        requireText(passwordHash, "passwordHash");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    /** Crea un usuario nuevo (sin id), activo y con rol {@link Role#USER} (RF-01). */
    public static User newSelfRegistered(String documentTypeCode, String documentNumber, String firstNames,
            String lastNames, String email, String phone, String passwordHash) {
        return new User(null, normalizeCode(documentTypeCode), documentNumber.trim(), firstNames.trim(),
                lastNames.trim(), normalizeEmail(email), phone == null ? null : phone.trim(), passwordHash, true,
                Set.of(Role.USER));
    }

    public User withId(Long newId) {
        return new User(newId, documentTypeCode, documentNumber, firstNames, lastNames, email, phone, passwordHash,
                active, roles);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
    }

    @Override
    public String toString() {
        return "User[id=%s, active=%s, roles=%s]".formatted(id, active, roles);
    }
}
