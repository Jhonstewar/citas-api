package com.fcv.citas.infrastructure.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalogo fijo {@code document_types} (solo lectura). */
@Entity
@Table(name = "document_types")
public class DocumentTypeJpaEntity {

    @Id
    private Short id;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected DocumentTypeJpaEntity() {
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }
}
