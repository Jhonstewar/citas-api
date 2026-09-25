package com.fcv.citas.application.user;

import java.util.Collection;
import java.util.Optional;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.affiliation.AffiliationQueries;
import com.fcv.citas.application.affiliation.AffiliationQueries.AffiliationView;
import com.fcv.citas.application.shared.Ownership;
import com.fcv.citas.domain.user.ProfilePolicy;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserNotFoundException;
import com.fcv.citas.domain.user.UserRepository;

/**
 * HU-008: el perfil propio ({@code GET/PUT /api/me}) con la afiliacion vigente (HU-009).
 *
 * <p>El titular sale siempre del token y pasa por la politica unica de ownership (HU-005): no hay
 * forma de pedir el perfil de otro (CA-04, CA-05). Los campos editables los declara el dominio
 * ({@link ProfilePolicy}, D25); un campo fijo en el cuerpo → 400 {@code FIELD_NOT_EDITABLE} sin
 * cambiar nada (CA-03).</p>
 */
public class ProfileUseCase {

    /** El usuario y su afiliacion vigente, si la tiene. */
    public record Profile(User user, Optional<AffiliationView> affiliation) {
    }

    private final UserRepository users;
    private final AffiliationQueries affiliations;
    private final TransactionRunner tx;

    public ProfileUseCase(UserRepository users, AffiliationQueries affiliations, TransactionRunner tx) {
        this.users = users;
        this.affiliations = affiliations;
        this.tx = tx;
    }

    /** CA-01: el perfil propio. Si el usuario del token ya no existe → 404, como antes de S4. */
    public Profile get(long userId) {
        User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
        return new Profile(user, affiliations.current(userId));
    }

    /**
     * CA-02 / CA-03 / CA-06: actualiza nombres, apellidos y telefono.
     *
     * @param sentFields los campos presentes en el cuerpo, ademas de los editables (aunque su valor
     *                   sea nulo): si alguno es fijo, la operacion se rechaza entera
     */
    public Profile update(long userId, Collection<String> sentFields, String firstNames, String lastNames,
            String phone) {
        ProfilePolicy.requireOnlyEditable(sentFields);
        tx.inTransaction(() -> {
            User current = Ownership.requireOwned(users.findById(userId), User::id, userId, "El usuario no existe");
            User updated = current.withContact(firstNames, lastNames, phone);
            users.updateContact(userId, updated.firstNames(), updated.lastNames(), updated.phone());
            return null;
        });
        return get(userId);
    }
}
