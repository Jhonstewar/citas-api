package com.fcv.citas.infrastructure.persistence.user;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.user.DocumentAlreadyRegisteredException;
import com.fcv.citas.domain.user.DocumentTypeCatalog;
import com.fcv.citas.domain.user.EmailAlreadyRegisteredException;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.domain.user.UnknownDocumentTypeException;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/** Adaptador JPA de los puertos {@link UserRepository} y {@link DocumentTypeCatalog}. */
@Component
class JpaUserRepositoryAdapter implements UserRepository, DocumentTypeCatalog {

    private final SpringDataUserRepository users;
    private final SpringDataRoleRepository roles;
    private final SpringDataDocumentTypeRepository documentTypes;

    JpaUserRepositoryAdapter(SpringDataUserRepository users, SpringDataRoleRepository roles,
            SpringDataDocumentTypeRepository documentTypes) {
        this.users = users;
        this.roles = roles;
        this.documentTypes = documentTypes;
    }

    @Override
    public boolean isActiveCode(String code) {
        return documentTypes.findByCodeAndActiveTrue(code).isPresent();
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return users.existsByEmail(normalizedEmail);
    }

    @Override
    public boolean existsByDocumentNumber(String documentNumber) {
        return users.existsByDocumentNumber(documentNumber);
    }

    @Override
    public User saveNew(User user) {
        DocumentTypeJpaEntity documentType = documentTypes.findByCodeAndActiveTrue(user.documentTypeCode())
                .orElseThrow(UnknownDocumentTypeException::new);
        Set<String> roleCodes = user.roles().stream().map(Role::name).collect(Collectors.toSet());
        Set<RoleJpaEntity> roleEntities = roles.findByCodeIn(roleCodes);
        if (roleEntities.size() != roleCodes.size()) {
            throw new IllegalStateException("Catalogo de roles incompleto: faltan seeds de V4");
        }
        UserJpaEntity entity = new UserJpaEntity(documentType, user.documentNumber(), user.firstNames(),
                user.lastNames(), user.email(), user.phone(), user.passwordHash(), user.active(), roleEntities);
        try {
            return toDomain(users.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Carrera entre la comprobacion previa y el INSERT: la BD es la ultima barrera (RF-01).
            String cause = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (cause.contains("uq_users_email")) {
                throw new EmailAlreadyRegisteredException();
            }
            if (cause.contains("uq_users_document_number")) {
                throw new DocumentAlreadyRegisteredException();
            }
            throw e;
        }
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return users.findByEmail(normalizedEmail).map(JpaUserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<User> findById(long id) {
        return users.findById(id).map(JpaUserRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByRole(Role role) {
        return users.existsByRoles_Code(role.name());
    }

    @Override
    public void updateContact(long userId, String firstNames, String lastNames, String phone) {
        UserJpaEntity entity = users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuario inexistente: " + userId));
        entity.updateContact(firstNames, lastNames, phone);
        users.saveAndFlush(entity);
    }

    private static User toDomain(UserJpaEntity e) {
        Set<Role> domainRoles = e.getRoles().stream().map(r -> Role.valueOf(r.getCode()))
                .collect(Collectors.toSet());
        return new User(e.getId(), e.getDocumentType().getCode(), e.getDocumentNumber(), e.getFirstNames(),
                e.getLastNames(), e.getEmail(), e.getPhone(), e.getPasswordHash(), e.isActive(), domainRoles);
    }
}
