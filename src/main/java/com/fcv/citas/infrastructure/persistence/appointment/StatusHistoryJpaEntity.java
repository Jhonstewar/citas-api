package com.fcv.citas.infrastructure.persistence.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla {@code appointment_status_history}: append-only (RN-12). Sin metodos de modificacion;
 * {@code changed_at} lo pone MySQL al insertar.
 */
@Entity
@Table(name = "appointment_status_history")
public class StatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private Long appointmentId;

    @Column(name = "status_id", nullable = false, updatable = false)
    private Short statusId;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "source", nullable = false, updatable = false, columnDefinition = "ENUM('SYSTEM','USER','ADMIN','PROFESSIONAL')")
    private String source;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    protected StatusHistoryJpaEntity() {
    }

    public StatusHistoryJpaEntity(Long appointmentId, short statusId, Long actorUserId, String source,
            String reason) {
        this.appointmentId = appointmentId;
        this.statusId = statusId;
        this.actorUserId = actorUserId;
        this.source = source;
        this.reason = reason;
    }
}
