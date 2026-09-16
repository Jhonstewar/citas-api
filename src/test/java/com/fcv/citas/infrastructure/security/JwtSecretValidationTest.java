package com.fcv.citas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** TRAMPA 2: un secreto HMAC menor de 256 bits impide construir la clave (y arrancar la app). */
class JwtSecretValidationTest {

    @Test
    void rejectsShortSecretWithoutEchoingIt() {
        String weak = "test-only-too-short-secret";
        assertThatThrownBy(() -> JwtConfig.buildHmacKey(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits")
                .hasMessageNotContaining(weak);
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> JwtConfig.buildHmacKey("  ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsSecretOf32Bytes() {
        assertThat(JwtConfig.buildHmacKey("test-only-0123456789abcdef-012345").getAlgorithm())
                .isEqualTo("HmacSHA256");
    }
}
