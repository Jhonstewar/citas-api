package com.fcv.citas.application.professional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.professional.ProfessionalQueries.Filter;
import com.fcv.citas.application.professional.ProfessionalQueries.ProfessionalView;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.catalog.SiteCatalog;
import com.fcv.citas.domain.catalog.Specialty;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.professional.Professional;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.professional.SpecialtyAssignment;
import com.fcv.citas.domain.shared.BusinessRuleException;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.user.DocumentTypeCatalog;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.domain.user.UnknownDocumentTypeException;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/**
 * HU-013 a HU-016: el ADMIN crea profesionales, les asigna especialidades y sedes y los activa o
 * desactiva. El alta es una sola transaccion: usuario PROFESSIONAL + perfil, o nada (HU-013 CA-06).
 */
public class ManageProfessionalsUseCase {

    private final UserRepository users;
    private final ProfessionalRepository professionals;
    private final ProfessionalQueries queries;
    private final SpecialtyRepository specialties;
    private final SiteCatalog sites;
    private final DocumentTypeCatalog documentTypes;
    private final PasswordHasher passwordHasher;
    private final TransactionRunner tx;

    public ManageProfessionalsUseCase(UserRepository users, ProfessionalRepository professionals,
            ProfessionalQueries queries, SpecialtyRepository specialties, SiteCatalog sites,
            DocumentTypeCatalog documentTypes, PasswordHasher passwordHasher, TransactionRunner tx) {
        this.users = users;
        this.professionals = professionals;
        this.queries = queries;
        this.specialties = specialties;
        this.sites = sites;
        this.documentTypes = documentTypes;
        this.passwordHasher = passwordHasher;
        this.tx = tx;
    }

    public List<ProfessionalView> list(Filter filter) {
        return queries.list(filter);
    }

    public ProfessionalView get(long id) {
        return queries.findById(id).orElseThrow(ManageProfessionalsUseCase::notFound);
    }

    /** Perfil propio del profesional autenticado ({@code GET /api/professional/me}). */
    public ProfessionalView me(long userId) {
        return queries.findByUserId(userId).orElseThrow(ManageProfessionalsUseCase::notFound);
    }

    public ProfessionalView create(CreateProfessionalCommand c) {
        SpecialtyAssignment assignment = SpecialtyAssignment.of(c.specialtyIds(), c.primarySpecialtyId());
        Set<Integer> siteIds = Professional.requireSites(c.siteIds() == null ? null : new HashSet<>(c.siteIds()));
        String email = User.normalizeEmail(c.email());
        String documentType = User.normalizeCode(c.documentTypeCode());
        String documentNumber = c.documentNumber().trim();

        long professionalId = tx.inTransaction(() -> {
            if (!documentTypes.isActiveCode(documentType)) {
                throw new UnknownDocumentTypeException();
            }
            if (users.existsByEmail(email)) {
                throw new DuplicateValueException("email", "Ya existe una cuenta con ese correo");
            }
            if (users.existsByDocumentNumber(documentNumber)) {
                throw new DuplicateValueException("documentNumber", "Ya existe una cuenta con ese documento");
            }
            requireActiveSpecialties(assignment);
            requireSites(siteIds);
            Professional draft = new Professional(null, 0, c.professionalCode(), c.licenseNumber(), true,
                    assignment, siteIds);
            if (professionals.existsByCode(draft.professionalCode())) {
                throw new DuplicateValueException("professionalCode", "Ya existe un profesional con ese código");
            }
            if (professionals.existsByLicense(draft.licenseNumber())) {
                throw new DuplicateValueException("licenseNumber", "Ya existe un profesional con esa matrícula");
            }
            User user = users.saveNew(new User(null, documentType, documentNumber, c.firstNames().trim(),
                    c.lastNames().trim(), email, c.phone() == null ? null : c.phone().trim(),
                    passwordHasher.hash(c.password()), true, Set.of(Role.PROFESSIONAL)));
            return professionals.saveNew(new Professional(null, user.id(), draft.professionalCode(),
                    draft.licenseNumber(), true, assignment, siteIds)).id();
        });
        return get(professionalId);
    }

    /** Datos de contacto editables; codigo y matricula quedan fijos (HU-013, INC-015). */
    public ProfessionalView updateContact(long id, String firstNames, String lastNames, String phone) {
        tx.inTransaction(() -> {
            Professional professional = professionals.findById(id).orElseThrow(ManageProfessionalsUseCase::notFound);
            users.updateContact(professional.userId(), firstNames.trim(), lastNames.trim(),
                    phone == null || phone.isBlank() ? null : phone.trim());
            return null;
        });
        return get(id);
    }

    public ProfessionalView assignSpecialties(long id, List<Integer> specialtyIds, Integer primaryId) {
        SpecialtyAssignment assignment = SpecialtyAssignment.of(specialtyIds, primaryId);
        tx.inTransaction(() -> {
            requireExisting(id);
            requireActiveSpecialties(assignment);
            professionals.replaceSpecialties(id, assignment);
            return null;
        });
        return get(id);
    }

    public ProfessionalView assignSites(long id, List<Integer> siteIds) {
        Set<Integer> sitesToAssign = Professional.requireSites(siteIds == null ? null : new HashSet<>(siteIds));
        tx.inTransaction(() -> {
            requireExisting(id);
            requireSites(sitesToAssign);
            professionals.replaceSites(id, sitesToAssign);
            return null;
        });
        return get(id);
    }

    /** HU-016: sin borrado; las citas existentes se conservan (D11). */
    public ProfessionalView setActive(long id, boolean active) {
        tx.inTransaction(() -> {
            requireExisting(id);
            professionals.setActive(id, active);
            return null;
        });
        return get(id);
    }

    private void requireExisting(long id) {
        professionals.findById(id).orElseThrow(ManageProfessionalsUseCase::notFound);
    }

    /** HU-014 CA-04 / RN-08: cada especialidad existe y esta activa. */
    private void requireActiveSpecialties(SpecialtyAssignment assignment) {
        for (Integer specialtyId : assignment.specialtyIds()) {
            Specialty specialty = specialties.findById(specialtyId)
                    .orElseThrow(() -> InvalidRequestException.field("specialtyIds",
                            "La especialidad " + specialtyId + " no existe"));
            if (!specialty.active()) {
                throw new BusinessRuleException("SPECIALTY_INACTIVE",
                        "La especialidad «" + specialty.name() + "» está inactiva y no se puede asignar");
            }
        }
    }

    /** HU-015 CA-03: sedes del catalogo fijo. */
    private void requireSites(Set<Integer> siteIds) {
        if (!sites.allActive(siteIds)) {
            throw InvalidRequestException.field("siteIds", "Alguna de las sedes no existe");
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("El profesional no existe");
    }
}
