package com.fcv.citas.application.auth;

import java.util.Optional;
import java.util.UUID;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.InvalidCredentialsException;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/**
 * HU-002: login por email y contraseña. Email inexistente, contraseña incorrecta y cuenta
 * inactiva producen la misma {@link InvalidCredentialsException}.
 */
public class LoginUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final TransactionRunner tx;
    private volatile String dummyHash;

    public LoginUseCase(UserRepository users, PasswordHasher passwordHasher, SessionIssuer sessionIssuer,
            TransactionRunner tx) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionIssuer = sessionIssuer;
        this.tx = tx;
    }

    public AuthSession login(String email, String password) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        if (found.isEmpty()) {
            // Igualar el coste temporal con el caso de email existente: no revelar existencia.
            passwordHasher.matches(password, dummyHash());
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!passwordHasher.matches(password, user.passwordHash()) || !user.active()) {
            throw new InvalidCredentialsException();
        }
        // Cada inicio de sesion abre una familia nueva de refresh tokens (DEC-002).
        return tx.inTransaction(() -> sessionIssuer.issue(user, UUID.randomUUID().toString()).session());
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordHasher.hash(UUID.randomUUID().toString());
            dummyHash = hash;
        }
        return hash;
    }
}
