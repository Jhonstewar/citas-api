package com.fcv.citas.application.auth;

import java.time.Clock;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.affiliation.Affiliation;
import com.fcv.citas.domain.affiliation.AffiliationRepository;
import com.fcv.citas.domain.affiliation.InsurancePlanCatalog;
import com.fcv.citas.domain.affiliation.InsurancePlanUnavailableException;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.auth.PasswordPolicy;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.DocumentAlreadyRegisteredException;
import com.fcv.citas.domain.user.DocumentTypeCatalog;
import com.fcv.citas.domain.user.EmailAlreadyRegisteredException;
import com.fcv.citas.domain.user.UnknownDocumentTypeException;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/**
 * HU-001: registro autonomo de una cuenta con rol USER. HU-009 añade la afiliacion OPCIONAL: si
 * el comando trae un plan de EPS, la cuenta y la afiliacion se crean en la misma transaccion, de
 * modo que un plan no seleccionable no deja un usuario a medias.
 */
public class RegisterUserUseCase {

    private final UserRepository users;
    private final DocumentTypeCatalog documentTypes;
    private final PasswordHasher passwordHasher;
    private final InsurancePlanCatalog insurancePlans;
    private final AffiliationRepository affiliations;
    private final TransactionRunner tx;
    private final Clock clock;

    public RegisterUserUseCase(UserRepository users, DocumentTypeCatalog documentTypes,
            PasswordHasher passwordHasher, InsurancePlanCatalog insurancePlans,
            AffiliationRepository affiliations, TransactionRunner tx, Clock clock) {
        this.users = users;
        this.documentTypes = documentTypes;
        this.passwordHasher = passwordHasher;
        this.insurancePlans = insurancePlans;
        this.affiliations = affiliations;
        this.tx = tx;
        this.clock = clock;
    }

    public User register(RegisterUserCommand command) {
        // D29: la entrada REST ya la valida; aqui se repite porque la regla es del dominio y no
        // puede depender de que cada adaptador se acuerde de aplicarla.
        PasswordPolicy.require(command.password(), "password");
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
            User saved = users.saveNew(user);
            affiliate(saved, command.insurancePlanId());
            return saved;
        });
    }

    /** RF-04: la afiliacion es opcional. Sin plan, el registro queda como estaba (HU-001). */
    private void affiliate(User saved, Integer insurancePlanId) {
        if (insurancePlanId == null) {
            return;
        }
        if (!insurancePlans.isSelectable(insurancePlanId)) {
            // Rompe la transaccion: el usuario tampoco se crea. No se distingue entre plan
            // inexistente, plan inactivo y EPS inactiva.
            throw new InsurancePlanUnavailableException();
        }
        affiliations.saveNew(Affiliation.startingToday(saved.id(), insurancePlanId, SystemZone.today(clock)));
    }
}
