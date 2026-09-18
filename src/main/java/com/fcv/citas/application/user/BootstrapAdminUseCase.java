package com.fcv.citas.application.user;

import java.util.Optional;
import java.util.Set;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/**
 * Decision D5: crea el primer ADMIN al arrancar si no existe ninguno y el entorno trae email y
 * contraseña. Sin credenciales en el repositorio; vacias = no se crea nadie. Idempotente: si ya
 * hay un ADMIN no hace nada, aunque cambien las variables.
 */
public class BootstrapAdminUseCase {

    /** Documento sintetico del ADMIN inicial: no es una persona real (PRD §8). */
    static final String DOCUMENT_TYPE = "CC";
    static final String DOCUMENT_NUMBER = "ADMIN-0001";

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TransactionRunner tx;

    public BootstrapAdminUseCase(UserRepository users, PasswordHasher passwordHasher, TransactionRunner tx) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tx = tx;
    }

    /** @return el ADMIN creado, o vacio si no hacia falta o no hay credenciales configuradas. */
    public Optional<User> bootstrap(String email, String rawPassword) {
        if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return Optional.empty();
        }
        String normalizedEmail = User.normalizeEmail(email);
        return tx.inTransaction(() -> {
            if (users.existsByRole(Role.ADMIN) || users.existsByEmail(normalizedEmail)) {
                return Optional.<User>empty();
            }
            User admin = new User(null, DOCUMENT_TYPE, DOCUMENT_NUMBER, "Administrador", "FCV Citas",
                    normalizedEmail, null, passwordHasher.hash(rawPassword), true, Set.of(Role.ADMIN));
            return Optional.of(users.saveNew(admin));
        });
    }
}
