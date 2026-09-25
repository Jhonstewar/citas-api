package com.fcv.citas.infrastructure.persistence.appointment;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla {@code appointments}: solo FKs a catalogos (3FN). Marcas de tiempo las rellena MySQL. */
@Entity
@Table(name = "appointments")
public class AppointmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_user_id", nullable = false)
    private Long patientUserId;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    @Column(name = "site_id", nullable = false)
    private Short siteId;

    @Column(name = "specialty_id", nullable = false)
    private Integer specialtyId;

    @Column(name = "status_id", nullable = false)
    private Short statusId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected AppointmentJpaEntity() {
    }

    public AppointmentJpaEntity(Long patientUserId, Long professionalId, int siteId, int specialtyId, short statusId,
            LocalDate scheduledDate, LocalTime startTime, LocalTime endTime) {
        this.patientUserId = patientUserId;
        this.professionalId = professionalId;
        this.siteId = (short) siteId;
        this.specialtyId = specialtyId;
        this.statusId = statusId;
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void setStatusId(short statusId) {
        this.statusId = statusId;
    }

    /** HU-031: la reprogramacion aprobada mueve la cita (dia, horas y sede, D21). */
    public void moveTo(int siteId, LocalDate scheduledDate, LocalTime startTime, LocalTime endTime) {
        this.siteId = (short) siteId;
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public Long getPatientUserId() {
        return patientUserId;
    }

    public Long getProfessionalId() {
        return professionalId;
    }

    public Short getSiteId() {
        return siteId;
    }

    public Integer getSpecialtyId() {
        return specialtyId;
    }

    public Short getStatusId() {
        return statusId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
