package com.fcv.citas.domain.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 en hexadecimal del refresh token opaco (DEC-002). No se usa BCrypt: el token ya tiene
 * 256 bits de entropia y el hash determinista permite buscarlo por indice UNIQUE.
 */
public final class RefreshTokenHasher {

    private RefreshTokenHasher() {
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
