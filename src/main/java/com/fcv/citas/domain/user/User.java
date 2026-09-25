package com.fcv.citas.domain.user;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.fcv.citas.domain.shared.InvalidRequestException;

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

    /** Limites de {@code users} (V1), los mismos que valida el registro. */
    public static final int MAX_NAMES = 100;
    public static final int MAX_PHONE = 30;

    /**
     * HU-008 · D25: cambia SOLO los datos editables del perfil ({@link ProfilePolicy#EDITABLE}).
     * Email, documento, contraseña, roles y estado se conservan. Los tres son obligatorios, con los
     * mismos limites que el registro (CA-06).
     */
    public User withContact(String newFirstNames, String newLastNames, String newPhone) {
        return new User(id, documentTypeCode, documentNumber, editable(newFirstNames, "firstNames", MAX_NAMES),
                editable(newLastNames, "lastNames", MAX_NAMES), email, editable(newPhone, "phone", MAX_PHONE),
                passwordHash, active, roles);
    }

    /** Mensajes por campo, como los de Bean Validation del registro ({@code fieldErrors}). */
    private static String editable(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw InvalidRequestException.field(field, "no debe estar vacío");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw InvalidRequestException.field(field, "admite como máximo " + max + " caracteres");
        }
        return trimmed;
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
