package com.fcv.citas.infrastructure.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalogo fijo {@code roles} (solo lectura). */
@Entity
@Table(name = "roles")
public class RoleJpaEntity {

    @Id
    private Short id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    protected RoleJpaEntity() {
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
}
