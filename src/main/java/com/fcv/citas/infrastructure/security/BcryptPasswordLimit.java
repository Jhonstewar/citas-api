package com.fcv.citas.infrastructure.security;

import java.nio.charset.StandardCharsets;

/**
 * Limite de longitud que impone BCrypt a la contraseña en claro: <strong>72 bytes UTF-8</strong>,
 * no 72 caracteres. {@code "ñ"} ocupa 2 bytes, {@code "€"} 3 y un emoji 4, asi que 40 x ñ son
 * 40 caracteres pero 80 bytes.
 *
 * <p>Por que hace falta una regla propia (Spring Security 6.5.x, tras el arreglo de
 * CVE-2025-22228):</p>
 * <ul>
 *   <li>{@code BCryptPasswordEncoder.encode} lanza {@link IllegalArgumentException} con mas de 72
 *       bytes: sin validar antes, el registro respondia 500.</li>
 *   <li>{@code BCryptPasswordEncoder.matches} NO rechaza esas contraseñas: {@code BCrypt.checkpw}
 *       las trunca a 72 bytes. Cualquier valor que empiece por los 72 bytes de la contraseña real
 *       coincidiria con su hash.</li>
 * </ul>
 *
 * <p>Es la unica fuente del limite: la usan {@link SpringPasswordHasher} y la validacion de
 * entrada del adaptador REST. Si se cambia de algoritmo (Argon2 no tiene este tope), hay que
 * revisar esta clase.</p>
 */
public final class BcryptPasswordLimit {

    /** Bytes UTF-8 que BCrypt usa de la contraseña. */
    public static final int MAX_BYTES = 72;

    private BcryptPasswordLimit() {
    }

    /**
     * {@code true} si la contraseña ocupa mas de {@link #MAX_BYTES} bytes en UTF-8, contados igual
     * que BCrypt ({@code String.getBytes(UTF_8)}).
     */
    public static boolean exceeds(CharSequence rawPassword) {
        // Cada unidad UTF-16 ocupa al menos un byte en UTF-8 (un surrogate suelto se codifica
        // como '?'), asi que con mas de 72 caracteres ya hay mas de 72 bytes. El atajo evita
        // codificar una cadena arbitrariamente larga enviada a proposito.
        return rawPassword.length() > MAX_BYTES
                || rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
