package com.fcv.citas.infrastructure.persistence.professional;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tabla {@code professional_specialties}. La columna generada {@code primary_marker} (unicidad de
 * la primaria) y {@code assigned_at} los calcula MySQL y no se mapean.
 */
@Entity
@Table(name = "professional_specialties")
public class ProfessionalSpecialtyJpaEntity {

    @Embeddable
    public record Key(
            @Column(name = "professional_id") Long professionalId,
            @Column(name = "specialty_id") Integer specialtyId) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected ProfessionalSpecialtyJpaEntity() {
    }

    public ProfessionalSpecialtyJpaEntity(long professionalId, int specialtyId, boolean primary) {
        this.id = new Key(professionalId, specialtyId);
        this.primary = primary;
    }

    public Key getId() {
        return id;
    }

    public boolean isPrimary() {
        return primary;
    }
}
