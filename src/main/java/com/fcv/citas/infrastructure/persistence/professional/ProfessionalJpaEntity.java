package com.fcv.citas.infrastructure.persistence.professional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla {@code professionals} (1:1 con {@code users}). Marcas de tiempo las rellena MySQL. */
@Entity
@Table(name = "professionals")
public class ProfessionalJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "professional_code", nullable = false, length = 30)
    private String professionalCode;

    @Column(name = "license_number", nullable = false, length = 40)
    private String licenseNumber;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ProfessionalJpaEntity() {
    }

    public ProfessionalJpaEntity(Long userId, String professionalCode, String licenseNumber, boolean active) {
        this.userId = userId;
        this.professionalCode = professionalCode;
        this.licenseNumber = licenseNumber;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getProfessionalCode() {
        return professionalCode;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public boolean isActive() {
        return active;
    }
}
