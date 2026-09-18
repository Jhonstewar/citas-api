package com.fcv.citas.domain.appointment;

import java.util.Objects;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Una fila del historial de estados (RF-19, HU-032): estado nuevo, actor cuando existe, origen y
 * motivo opcional. La fecha y hora las pone la base al insertar. Inmutable (RN-12).
 */
public record StatusChange(AppointmentStatus status, Long actorUserId, AuditSource source, String reason) {

    public static final int MAX_REASON = 500;

    public StatusChange {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        // HU-032 CA-02: USER, ADMIN y PROFESSIONAL siempre con actor; SYSTEM puede no tenerlo.
        if (source != AuditSource.SYSTEM && actorUserId == null) {
            throw new IllegalArgumentException("El origen " + source + " exige actor");
        }
        if (reason != null) {
            reason = reason.trim();
            if (reason.isEmpty()) {
                reason = null;
            } else if (reason.length() > MAX_REASON) {
                throw InvalidRequestException.field("reason", "El motivo admite como máximo 500 caracteres");
            }
        }
    }
}
