package com.fcv.citas.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.auth.PasswordHasher;

/**
 * Adapta el {@link PasswordEncoder} delegante (BCrypt) al puerto del dominio y aplica el limite
 * de {@link BcryptPasswordLimit} en los dos sentidos, sin depender de como lo trate cada version
 * de Spring Security.
 */
@Component
class SpringPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    SpringPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * La validacion de entrada ya rechaza con 400 las contraseñas de mas de 72 bytes; llegar aqui
     * con una es un error de programacion. El mensaje no incluye la contraseña.
     */
    @Override
    public String hash(String rawPassword) {
        if (BcryptPasswordLimit.exceeds(rawPassword)) {
            throw new IllegalArgumentException(
                    "La contraseña supera los " + BcryptPasswordLimit.MAX_BYTES + " bytes que admite BCrypt");
        }
        return encoder.encode(rawPassword);
    }

    /**
     * Una contraseña de mas de 72 bytes nunca pudo registrarse, asi que no coincide con ningun
     * hash. Se responde {@code false} ANTES de llamar a BCrypt, que la truncaria a 72 bytes y
     * daria por buena cualquier contraseña que compartiera ese prefijo (CVE-2025-22228). El
     * atajo no revela si el email existe: el caso de uso lo recorre igual en los dos caminos.
     */
    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (BcryptPasswordLimit.exceeds(rawPassword)) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }
}
