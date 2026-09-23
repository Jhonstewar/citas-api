package com.fcv.citas.infrastructure.persistence.affiliation;

import org.springframework.stereotype.Component;

import com.fcv.citas.domain.affiliation.Affiliation;
import com.fcv.citas.domain.affiliation.AffiliationRepository;

/** Adaptador JPA del puerto {@link AffiliationRepository}. */
@Component
class JpaAffiliationRepositoryAdapter implements AffiliationRepository {

    private final SpringDataAffiliationRepository affiliations;

    JpaAffiliationRepositoryAdapter(SpringDataAffiliationRepository affiliations) {
        this.affiliations = affiliations;
    }

    @Override
    public Affiliation saveNew(Affiliation affiliation) {
        AffiliationJpaEntity entity = new AffiliationJpaEntity(affiliation.userId(), affiliation.epsPlanId(),
                affiliation.current(), affiliation.startedOn());
        // Flush inmediato: si una restriccion de la tabla rechaza la fila, el fallo debe ocurrir
        // dentro de la transaccion del registro y no al cerrar la sesion (HU-009).
        return affiliation.withId(affiliations.saveAndFlush(entity).getId());
    }
}
