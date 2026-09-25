package com.fcv.citas.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.shared.InvalidRequestException;

/** HU-008 · D25: el conjunto de campos editables del perfil esta declarado en el servidor. */
class ProfilePolicyTest {

    private static final User USER = new User(9L, "CC", "123", "Ana", "Pérez", "ana@example.com", "300",
            "{bcrypt}hash", true, Set.of(Role.USER));

    @Test
    void theEditableAndFixedFieldsAreDeclared() {
        assertThat(ProfilePolicy.EDITABLE).containsExactly("firstNames", "lastNames", "phone");
        assertThat(ProfilePolicy.FIXED).containsExactly("email", "documentType", "documentNumber", "password",
                "roles");
    }

    @Test
    void onlyEditableFieldsPass() {
        assertThatCode(() -> ProfilePolicy.requireOnlyEditable(List.of("firstNames", "lastNames", "phone")))
                .doesNotThrowAnyException();
        // Un campo desconocido no es un campo fijo del perfil: se ignora, como cualquier JSON extra.
        assertThatCode(() -> ProfilePolicy.requireOnlyEditable(List.of("phone", "unknown")))
                .doesNotThrowAnyException();
    }

    /** CA-03: cualquier campo fijo presente → 400 FIELD_NOT_EDITABLE con ese campo. */
    @Test
    void eachFixedFieldIsRejectedByName() {
        for (String fixed : ProfilePolicy.FIXED) {
            assertThatThrownBy(() -> ProfilePolicy.requireOnlyEditable(List.of("firstNames", fixed)))
                    .isInstanceOfSatisfying(FieldNotEditableException.class, e -> {
                        assertThat(e.code()).isEqualTo("FIELD_NOT_EDITABLE");
                        assertThat(e.field()).isEqualTo(fixed);
                        assertThat(e.getMessage()).isNotBlank();
                    });
        }
    }

    @Test
    void withContactChangesOnlyTheEditableData() {
        User updated = USER.withContact("  Ana María ", " Pérez Gómez ", " 3009998877 ");

        assertThat(updated.firstNames()).isEqualTo("Ana María");
        assertThat(updated.lastNames()).isEqualTo("Pérez Gómez");
        assertThat(updated.phone()).isEqualTo("3009998877");
        assertThat(updated.email()).isEqualTo(USER.email());
        assertThat(updated.documentNumber()).isEqualTo(USER.documentNumber());
        assertThat(updated.roles()).isEqualTo(USER.roles());
        assertThat(updated.passwordHash()).isEqualTo(USER.passwordHash());
    }

    /** CA-06: mismos limites que el registro (100, 100 y 30), obligatorios. */
    @Test
    void withContactValidatesLikeTheRegistration() {
        assertField(() -> USER.withContact(" ", "P", "300"), "firstNames");
        assertField(() -> USER.withContact(null, "P", "300"), "firstNames");
        assertField(() -> USER.withContact("A".repeat(101), "P", "300"), "firstNames");
        assertField(() -> USER.withContact("A", "", "300"), "lastNames");
        assertField(() -> USER.withContact("A", "P".repeat(101), "300"), "lastNames");
        assertField(() -> USER.withContact("A", "P", " "), "phone");
        assertField(() -> USER.withContact("A", "P", "3".repeat(31)), "phone");
        assertThatCode(() -> USER.withContact("A".repeat(100), "P".repeat(100), "3".repeat(30)))
                .doesNotThrowAnyException();
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> {
                    assertThat(e.field()).isEqualTo(field);
                    assertThat(e.code()).isEqualTo("VALIDATION");
                });
    }
}
