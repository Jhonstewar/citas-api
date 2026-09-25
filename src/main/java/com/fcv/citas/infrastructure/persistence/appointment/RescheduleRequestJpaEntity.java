package com.fcv.citas.infrastructure.persistence.appointment;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla {@code reschedule_requests} (V3 + V10). Solo se inserta con JPA y se bloquea con JPQL; la
 * decision se escribe con un UPDATE nativo que pone {@code decided_at} con la hora de la base.
 * {@code active_marker} (columna generada) y las marcas de tiempo no se mapean: las mantiene MySQL.
 */
@Entity
@Table(name = "reschedule_requests")
public class RescheduleRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "status_id", nullable = false)
    private Short statusId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "previous_date", nullable = false)
    private LocalDate previousDate;

    @Column(name = "previous_start_time", nullable = false)
    private LocalTime previousStartTime;

    @Column(name = "previous_end_time", nullable = false)
    private LocalTime previousEndTime;

    @Column(name = "previous_site_id", nullable = false)
    private Short previousSiteId;

    @Column(name = "proposed_date", nullable = false)
    private LocalDate proposedDate;

    @Column(name = "proposed_start_time", nullable = false)
    private LocalTime proposedStartTime;

    @Column(name = "proposed_end_time", nullable = false)
    private LocalTime proposedEndTime;

    @Column(name = "proposed_site_id", nullable = false)
    private Short proposedSiteId;

    @Column(name = "request_reason", length = 500)
    private String requestReason;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    protected RescheduleRequestJpaEntity() {
    }

    RescheduleRequestJpaEntity(long appointmentId, short statusId, long requestedByUserId, LocalDate previousDate,
            LocalTime previousStartTime, LocalTime previousEndTime, int previousSiteId, LocalDate proposedDate,
            LocalTime proposedStartTime, LocalTime proposedEndTime, int proposedSiteId, String requestReason) {
        this.appointmentId = appointmentId;
        this.statusId = statusId;
        this.requestedByUserId = requestedByUserId;
        this.previousDate = previousDate;
        this.previousStartTime = previousStartTime;
        this.previousEndTime = previousEndTime;
        this.previousSiteId = (short) previousSiteId;
        this.proposedDate = proposedDate;
        this.proposedStartTime = proposedStartTime;
        this.proposedEndTime = proposedEndTime;
        this.proposedSiteId = (short) proposedSiteId;
        this.requestReason = requestReason;
    }

    Long getId() {
        return id;
    }

    Long getAppointmentId() {
        return appointmentId;
    }

    Short getStatusId() {
        return statusId;
    }

    Long getRequestedByUserId() {
        return requestedByUserId;
    }

    LocalDate getPreviousDate() {
        return previousDate;
    }

    LocalTime getPreviousStartTime() {
        return previousStartTime;
    }

    LocalTime getPreviousEndTime() {
        return previousEndTime;
    }

    Short getPreviousSiteId() {
        return previousSiteId;
    }

    LocalDate getProposedDate() {
        return proposedDate;
    }

    LocalTime getProposedStartTime() {
        return proposedStartTime;
    }

    LocalTime getProposedEndTime() {
        return proposedEndTime;
    }

    Short getProposedSiteId() {
        return proposedSiteId;
    }

    String getRequestReason() {
        return requestReason;
    }

    Long getDecidedByUserId() {
        return decidedByUserId;
    }

    String getDecisionReason() {
        return decisionReason;
    }
}
