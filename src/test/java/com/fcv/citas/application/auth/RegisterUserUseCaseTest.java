package com.fcv.citas.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.user.DocumentAlreadyRegisteredException;
import com.fcv.citas.domain.user.EmailAlreadyRegisteredException;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.domain.user.UnknownDocumentTypeException;
import com.fcv.citas.domain.user.User;

class RegisterUserUseCaseTest {

    private Fakes.InMemoryUsers users;
    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        users = new Fakes.InMemoryUsers();
        useCase = new RegisterUserUseCase(users, Fakes.DOCUMENT_TYPES, new Fakes.FakeHasher(), Fakes.DIRECT_TX);
    }

    private static RegisterUserCommand command(String email, String document) {
        return new RegisterUserCommand("Ana", "Pérez", "cc", document, email, "3001234567", "Secreta#123");
    }

    @Test
    void registersActiveUserWithRoleUserAndHashedPassword() {
        User user = useCase.register(command("  Paciente.Demo@Example.com ", "100200300"));

        assertThat(user.id()).isNotNull();
        assertThat(user.roles()).containsExactly(Role.USER);
        assertThat(user.active()).isTrue();
        assertThat(user.email()).isEqualTo("paciente.demo@example.com");
        assertThat(user.documentTypeCode()).isEqualTo("CC");
        assertThat(user.passwordHash()).isNotEqualTo("Secreta#123");
        assertThat(user.toString()).doesNotContain(user.passwordHash());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        useCase.register(command("paciente.demo@example.com", "1"));

        assertThatThrownBy(() -> useCase.register(command("PACIENTE.demo@example.com", "2")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("email ya está registrado");
        assertThat(users.users).hasSize(1);
    }

    @Test
    void rejectsDuplicateDocument() {
        useCase.register(command("a@example.com", "555"));

        assertThatThrownBy(() -> useCase.register(command("b@example.com", "555")))
                .isInstanceOf(DocumentAlreadyRegisteredException.class)
                .hasMessageContaining("documento ya está registrado");
        assertThat(users.users).hasSize(1);
    }

    @Test
    void rejectsUnknownDocumentType() {
        RegisterUserCommand bad = new RegisterUserCommand("Ana", "Pérez", "XX", "1", "a@example.com", "300", "pw");

        assertThatThrownBy(() -> useCase.register(bad)).isInstanceOf(UnknownDocumentTypeException.class);
        assertThat(users.users).isEmpty();
    }
}
