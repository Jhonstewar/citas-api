package com.fcv.citas.infrastructure.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Tabla {@code specialties}. {@code created_at}/{@code updated_at} los rellena MySQL. */
@Entity
@Table(name = "specialties")
public class SpecialtyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "appointment_type_id", nullable = false)
    private AppointmentTypeJpaEntity appointmentType;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected SpecialtyJpaEntity() {
    }

    public SpecialtyJpaEntity(AppointmentTypeJpaEntity appointmentType, String code, String name,
            short durationMinutes, boolean active) {
        this.appointmentType = appointmentType;
        this.code = code;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.active = active;
    }

    public void update(AppointmentTypeJpaEntity newType, String newName, short newDuration, boolean newActive) {
        this.appointmentType = newType;
        this.name = newName;
        this.durationMinutes = newDuration;
        this.active = newActive;
    }

    public Integer getId() {
        return id;
    }

    public AppointmentTypeJpaEntity getAppointmentType() {
        return appointmentType;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isActive() {
        return active;
    }
}
