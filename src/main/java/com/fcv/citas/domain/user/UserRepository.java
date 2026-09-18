package com.fcv.citas.domain.user;

import java.util.Optional;

/** Puerto de salida para la persistencia de usuarios. */
public interface UserRepository {

    boolean existsByEmail(String normalizedEmail);

    boolean existsByDocumentNumber(String documentNumber);

    /**
     * Persiste un usuario nuevo con sus roles.
     *
     * @throws EmailAlreadyRegisteredException    si la BD rechaza el email por unicidad (carrera)
     * @throws DocumentAlreadyRegisteredException si la BD rechaza el documento por unicidad (carrera)
     */
    User saveNew(User user);

    Optional<User> findByEmail(String normalizedEmail);

    Optional<User> findById(long id);

    boolean existsByRole(Role role);

    /** Actualiza los datos de contacto editables; email, documento y roles no cambian. */
    void updateContact(long userId, String firstNames, String lastNames, String phone);
}
