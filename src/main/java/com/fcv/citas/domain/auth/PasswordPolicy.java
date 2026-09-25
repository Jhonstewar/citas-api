package com.fcv.citas.domain.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Politica de contraseña D29 (INC-001). Se aplica solo al FIJAR una contraseña —registro,
 * restablecimiento y alta de profesional por el ADMIN—, nunca al iniciar sesion: las cuentas
 * creadas antes de la politica siguen entrando.
 *
 * <ul>
 *   <li>minimo {@value #MIN_LENGTH} caracteres, contados como unidades UTF-16
 *       ({@code String.length()}), igual que {@code value.length} en {@code citas-web};</li>
 *   <li>como maximo {@value #MAX_UTF8_BYTES} bytes en UTF-8: es lo que BCrypt usa entero. La ñ y
 *       las vocales con tilde ocupan 2 bytes y los emojis 4;</li>
 *   <li>al menos una letra —cualquier letra Unicode, {@code \p{L}}: la ñ y las vocales con tilde
 *       cuentan— y al menos un digito {@code 0-9}. Es la misma definicion que usa el cliente.</li>
 * </ul>
 *
 * <p>Es la unica fuente de la regla: la usan la validacion de entrada del adaptador REST (que
 * produce {@code fieldErrors}) y los casos de uso que fijan una contraseña, como defensa en
 * profundidad. Ningun mensaje incluye la contraseña.</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_UTF8_BYTES = 72;

    public static final String TOO_SHORT = "debe tener al menos " + MIN_LENGTH + " caracteres";
    public static final String TOO_LONG = "no debe superar " + MAX_UTF8_BYTES
            + " bytes en UTF-8 (la ñ y las vocales con tilde ocupan 2 bytes; los emojis, 4)";
    public static final String LETTER_AND_DIGIT = "debe combinar al menos una letra y un número";

    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private PasswordPolicy() {
    }

    /**
     * Primer incumplimiento, en orden fijo (longitud minima, longitud maxima, letra y numero), o
     * vacio si la contraseña cumple. {@code null} y el texto en blanco no se evaluan: su
     * obligatoriedad la decide quien llama ({@code @NotBlank} en la entrada REST).
     */
    public static Optional<String> violation(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return Optional.empty();
        }
        if (rawPassword.length() < MIN_LENGTH) {
            return Optional.of(TOO_SHORT);
        }
        // Cada unidad UTF-16 ocupa al menos un byte, asi que con mas de 72 ya sobran bytes: el
        // atajo evita codificar una cadena arbitrariamente larga enviada a proposito.
        if (rawPassword.length() > MAX_UTF8_BYTES
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            return Optional.of(TOO_LONG);
        }
        if (!LETTER.matcher(rawPassword).find() || !DIGIT.matcher(rawPassword).find()) {
            return Optional.of(LETTER_AND_DIGIT);
        }
        return Optional.empty();
    }

    /**
     * Rechaza con {@code 400 VALIDATION} atribuido a {@code field} una contraseña que no cumple,
     * incluida la ausente o en blanco.
     */
    public static void require(String rawPassword, String field) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw InvalidRequestException.field(field, "no debe estar vacío");
        }
        violation(rawPassword).ifPresent(message -> {
            throw InvalidRequestException.field(field, message);
        });
    }
}
