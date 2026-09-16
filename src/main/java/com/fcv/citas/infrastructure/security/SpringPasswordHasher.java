package com.fcv.citas.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.auth.PasswordHasher;

/** Adapta el {@link PasswordEncoder} delegante (BCrypt) al puerto del dominio. */
@Component
class SpringPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    SpringPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
