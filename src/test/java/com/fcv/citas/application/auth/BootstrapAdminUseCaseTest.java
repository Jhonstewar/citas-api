package com.fcv.citas.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fcv.citas.application.user.BootstrapAdminUseCase;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.domain.user.User;

/** Decision D5: el primer ADMIN nace del entorno, una sola vez y nunca sin credenciales. */
class BootstrapAdminUseCaseTest {

    private final Fakes.InMemoryUsers users = new Fakes.InMemoryUsers();
    private final Fakes.FakeHasher hasher = new Fakes.FakeHasher();
    private final BootstrapAdminUseCase bootstrap = new BootstrapAdminUseCase(users, hasher, Fakes.DIRECT_TX);

    @Test
    void createsAnAdminWithHashedPasswordWhenNoneExists() {
        User admin = bootstrap.bootstrap(" Admin@Citas.Test ", "Clave-Inicial#1").orElseThrow();

        assertThat(admin.roles()).containsExactly(Role.ADMIN);
        assertThat(admin.email()).isEqualTo("admin@citas.test");
        assertThat(admin.passwordHash()).isNotEqualTo("Clave-Inicial#1");
        assertThat(hasher.matches("Clave-Inicial#1", admin.passwordHash())).isTrue();
    }

    @Test
    void doesNothingWithoutCredentials() {
        assertThat(bootstrap.bootstrap("", "x")).isEmpty();
        assertThat(bootstrap.bootstrap("admin@citas.test", " ")).isEmpty();
        assertThat(bootstrap.bootstrap(null, null)).isEmpty();
        assertThat(users.users).isEmpty();
    }

    @Test
    void isIdempotentOnceAnAdminExists() {
        bootstrap.bootstrap("admin@citas.test", "Clave-Inicial#1");
        assertThat(bootstrap.bootstrap("otro@citas.test", "Otra-Clave#2")).isEmpty();
        assertThat(users.users).hasSize(1);
    }

    @Test
    void doesNotPromoteAnExistingUserWithTheSameEmail() {
        users.saveNew(new User(null, "CC", "123", "Ana", "Pérez", "admin@citas.test", null, "{fake}x", true,
                Set.of(Role.USER)));
        assertThat(bootstrap.bootstrap("admin@citas.test", "Clave-Inicial#1")).isEmpty();
        assertThat(users.users).hasSize(1);
        assertThat(users.users.get(0).roles()).containsExactly(Role.USER);
    }
}
