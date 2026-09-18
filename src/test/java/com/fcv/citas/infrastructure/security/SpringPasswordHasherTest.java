package com.fcv.citas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * El limite de BCrypt se mide en bytes UTF-8, igual que lo mide BCrypt, y el adaptador lo aplica
 * en los dos sentidos: no hashea lo que no cabe y no da por buena una contraseña que solo comparte
 * los 72 primeros bytes con la real.
 */
class SpringPasswordHasherTest {

    private static final String EMOJI = "😀";

    private final SpringPasswordHasher hasher =
            new SpringPasswordHasher(PasswordEncoderFactories.createDelegatingPasswordEncoder());

    static Stream<Arguments> passwordsAroundTheLimit() {
        return Stream.of(
                Arguments.of("a".repeat(72), false),
                Arguments.of("a".repeat(73), true),
                Arguments.of("ñ".repeat(36), false),
                Arguments.of("ñ".repeat(36) + "a", true),
                Arguments.of("ñ".repeat(40), true),
                Arguments.of("€".repeat(24), false),
                Arguments.of("€".repeat(25), true),
                Arguments.of(EMOJI.repeat(18), false),
                Arguments.of(EMOJI.repeat(19), true),
                // Surrogate suelto: String.getBytes(UTF_8) lo codifica como '?', un byte.
                Arguments.of("\uD83D".repeat(72), false),
                Arguments.of("\uD83D".repeat(73), true));
    }

    @ParameterizedTest
    @MethodSource("passwordsAroundTheLimit")
    void limitCountsUtf8BytesExactlyLikeBcrypt(String password, boolean exceeds) {
        assertThat(BcryptPasswordLimit.exceeds(password)).isEqualTo(exceeds);
        // Misma cuenta que hace BCrypt.hashpw/checkpw antes de procesar la contraseña.
        assertThat(BcryptPasswordLimit.exceeds(password))
                .isEqualTo(password.getBytes(StandardCharsets.UTF_8).length > BcryptPasswordLimit.MAX_BYTES);
    }

    @Test
    void passwordsOfExactly72BytesAreHashedAndVerified() {
        for (String password : List.of("a".repeat(72), "ñ".repeat(36), EMOJI.repeat(18))) {
            String hash = hasher.hash(password);

            assertThat(hash).startsWith("{bcrypt}$2");
            assertThat(hasher.matches(password, hash)).isTrue();
            assertThat(hasher.matches(password.substring(0, password.length() - 2), hash)).isFalse();
        }
    }

    @Test
    void passwordThatOnlySharesTheFirst72BytesDoesNotMatch() {
        String real = "ñ".repeat(36);
        String hash = hasher.hash(real);

        // BCrypt.checkpw truncaria estas tres a los mismos 72 bytes que `real`.
        assertThat(hasher.matches(real + "x", hash)).isFalse();
        assertThat(hasher.matches(real + "ñ", hash)).isFalse();
        assertThat(hasher.matches(real + "-lo-que-sea", hash)).isFalse();
    }

    @Test
    void oversizedPasswordNeverMatchesAndIsNotEncoded() {
        String hash = hasher.hash("Clave-Secreta#2026");

        assertThat(hasher.matches("x".repeat(1_000_000), hash)).isFalse();
    }

    @Test
    void hashRefusesPasswordOver72BytesWithoutEchoingIt() {
        String password = "ñ".repeat(40);

        assertThatThrownBy(() -> hasher.hash(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes")
                .hasMessageNotContaining(password);
    }
}
