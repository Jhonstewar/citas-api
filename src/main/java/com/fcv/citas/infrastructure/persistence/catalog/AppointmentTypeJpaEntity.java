package com.fcv.citas.infrastructure.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalogo fijo {@code appointment_types} (solo lectura). */
@Entity
@Table(name = "appointment_types")
public class AppointmentTypeJpaEntity {

    @Id
    private Short id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "requires_admin_approval", nullable = false)
    private boolean requiresAdminApproval;

    protected AppointmentTypeJpaEntity() {
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
}
