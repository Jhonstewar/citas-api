package com.fcv.citas.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fcv.citas.domain.shared.InvalidRequestException;

/** D29 (INC-001): minimo 8, letra Unicode y digito, maximo 72 bytes UTF-8. */
class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "abcdefg1",          // justo 8
        "Clave-Secreta#2026",
        "ñandú123",          // la ñ y la u con tilde cuentan como letras (\p{L})
        "12345678é",
        "ÁÉÍÓÚ2026",
    })
    void acceptsPasswordsThatMeetThePolicy(String password) {
        assertThat(PasswordPolicy.violation(password)).isEmpty();
    }

    @Test
    void rejectsShortPasswordsFirst() {
        assertThat(PasswordPolicy.violation("abc123")).contains(PasswordPolicy.TOO_SHORT);
        assertThat(PasswordPolicy.violation("a1")).contains(PasswordPolicy.TOO_SHORT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "abcdefgh", "12345678", "ñññññññññ", "!!!!####$$", "😀😀😀😀1111" })
    void requiresALetterAndADigit(String password) {
        assertThat(PasswordPolicy.violation(password)).contains(PasswordPolicy.LETTER_AND_DIGIT);
    }

    @Test
    void measuresTheMaximumInUtf8Bytes() {
        String exactly72 = "ñ".repeat(35) + "a1";
        assertThat(exactly72.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(PasswordPolicy.violation(exactly72)).isEmpty();

        String seventyThree = "ñ".repeat(35) + "a12";
        assertThat(seventyThree.getBytes(StandardCharsets.UTF_8)).hasSize(73);
        assertThat(PasswordPolicy.violation(seventyThree)).contains(PasswordPolicy.TOO_LONG);
        // 40 caracteres, 80 bytes: la regla en caracteres lo dejaria pasar.
        assertThat(PasswordPolicy.violation("ñ".repeat(39) + "1")).contains(PasswordPolicy.TOO_LONG);
    }

    @Test
    void leavesBlankToTheCaller() {
        assertThat(PasswordPolicy.violation(null)).isEmpty();
        assertThat(PasswordPolicy.violation("")).isEmpty();
        assertThat(PasswordPolicy.violation("   ")).isEmpty();
    }

    @Test
    void requireThrowsAValidationErrorOnTheGivenFieldWithoutThePassword() {
        assertThatThrownBy(() -> PasswordPolicy.require("abcdefgh", "newPassword"))
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> {
                    assertThat(e.code()).isEqualTo("VALIDATION");
                    assertThat(e.field()).isEqualTo("newPassword");
                    assertThat(e.getMessage()).isEqualTo(PasswordPolicy.LETTER_AND_DIGIT).doesNotContain("abcdefgh");
                });
        assertThatThrownBy(() -> PasswordPolicy.require(" ", "password"))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.field()).isEqualTo("password"));
    }
}
