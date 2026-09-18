package com.fcv.citas.domain.appointment;

/** Origen de un cambio de estado (RF-19). {@code PROFESSIONAL} llega con V5 (decision D8). */
public enum AuditSource {
    SYSTEM,
    USER,
    ADMIN,
    PROFESSIONAL
}
