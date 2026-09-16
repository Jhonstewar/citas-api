package com.fcv.citas.application.auth;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.user.DocumentAlreadyRegisteredException;
import com.fcv.citas.domain.user.DocumentTypeCatalog;
import com.fcv.citas.domain.user.EmailAlreadyRegisteredException;
import com.fcv.citas.domain.user.UnknownDocumentTypeException;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/** HU-001: registro autonomo de una cuenta con rol USER. */
public class RegisterUserUseCase {

    private final UserRepository users;
    private final DocumentTypeCatalog documentTypes;
    private final PasswordHasher passwordHasher;
    private final TransactionRunner tx;

    public RegisterUserUseCase(UserRepository users, DocumentTypeCatalog documentTypes,
            PasswordHasher passwordHasher, TransactionRunner tx) {
        this.users = users;
        this.documentTypes = documentTypes;
        this.passwordHasher = passwordHasher;
        this.tx = tx;
    }

    public User register(RegisterUserCommand command) {
        String documentTypeCode = User.normalizeCode(command.documentTypeCode());
        String email = User.normalizeEmail(command.email());
        String documentNumber = command.documentNumber().trim();

        return tx.inTransaction(() -> {
            if (!documentTypes.isActiveCode(documentTypeCode)) {
                throw new UnknownDocumentTypeException();
            }
            if (users.existsByEmail(email)) {
                throw new EmailAlreadyRegisteredException();
            }
            if (users.existsByDocumentNumber(documentNumber)) {
                throw new DocumentAlreadyRegisteredException();
            }
            User user = User.newSelfRegistered(documentTypeCode, documentNumber, command.firstNames(),
                    command.lastNames(), email, command.phone(), passwordHasher.hash(command.password()));
            return users.saveNew(user);
        });
    }
}
