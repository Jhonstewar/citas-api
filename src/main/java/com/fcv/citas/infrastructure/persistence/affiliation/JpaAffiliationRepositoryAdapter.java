package com.fcv.citas.infrastructure.persistence.affiliation;

import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.fcv.citas.domain.affiliation.Affiliation;
import com.fcv.citas.domain.affiliation.AffiliationRepository;

/** Adaptador JPA del puerto {@link AffiliationRepository}. */
@Component
class JpaAffiliationRepositoryAdapter implements AffiliationRepository {

    private final SpringDataAffiliationRepository affiliations;
    private final EntityManager em;

    JpaAffiliationRepositoryAdapter(SpringDataAffiliationRepository affiliations, EntityManager em) {
        this.affiliations = affiliations;
        this.em = em;
    }

    @Override
    public Affiliation saveNew(Affiliation affiliation) {
        AffiliationJpaEntity entity = new AffiliationJpaEntity(affiliation.userId(), affiliation.epsPlanId(),
                affiliation.current(), affiliation.startedOn());
        // Flush inmediato: si una restriccion de la tabla rechaza la fila, el fallo debe ocurrir
        // dentro de la transaccion del registro y no al cerrar la sesion (HU-009).
        return affiliation.withId(affiliations.saveAndFlush(entity).getId());
    }

    @Override
    public Optional<Affiliation> lockCurrent(long userId) {
        // Se bloquea la fila del USUARIO y no solo la afiliacion: si aun no tiene ninguna vigente no
        // hay fila que bloquear, y dos altas simultaneas chocarian en uq_affiliations_user_current en
        // vez de serializarse.
        em.createNativeQuery("SELECT id FROM users WHERE id = ?1 FOR UPDATE").setParameter(1, userId)
                .getResultList();
        return affiliations.lockCurrentByUserId(userId).map(JpaAffiliationRepositoryAdapter::toDomain);
    }

    @Override
    public void close(Affiliation closed) {
        AffiliationJpaEntity entity = affiliations.findById(closed.id())
                .orElseThrow(() -> new IllegalStateException("Afiliacion inexistente: " + closed.id()));
        entity.close(closed.endedOn());
        // Flush antes de insertar la nueva: la vigente debe dejar de serlo en la BD primero, o la
        // nueva chocaria con uq_affiliations_user_current.
        affiliations.saveAndFlush(entity);
    }

    private static Affiliation toDomain(AffiliationJpaEntity e) {
        return new Affiliation(e.getId(), e.getUserId(), e.getEpsPlanId(), e.isCurrent(), e.getStartedOn(),
                e.getEndedOn());
    }
}
