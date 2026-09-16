package com.fcv.citas.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.fcv.citas.domain.auth.SecureTokenGenerator;

/** Refresh token opaco: 32 bytes de {@link SecureRandom} en Base64 URL sin relleno (DEC-002). */
@Component
class SecureRandomTokenGenerator implements SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
