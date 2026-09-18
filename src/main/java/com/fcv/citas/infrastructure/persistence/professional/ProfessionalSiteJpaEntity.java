package com.fcv.citas.infrastructure.persistence.professional;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Tabla {@code professional_sites}: sedes habilitadas del profesional (RN-07). */
@Entity
@Table(name = "professional_sites")
public class ProfessionalSiteJpaEntity {

    @Embeddable
    public record Key(
            @Column(name = "professional_id") Long professionalId,
            @Column(name = "site_id") Short siteId) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    protected ProfessionalSiteJpaEntity() {
    }

    public ProfessionalSiteJpaEntity(long professionalId, int siteId) {
        this.id = new Key(professionalId, (short) siteId);
    }

    public Key getId() {
        return id;
    }
}
