package com.fcv.citas.application.appointment;

/** HU-035 (D-E): las unicas transiciones que se publican hacia automatizaciones. */
public enum AppointmentEventType {
    APPOINTMENT_APPROVED,
    APPOINTMENT_REJECTED,
    APPOINTMENT_CANCELLED,
    RESCHEDULE_APPROVED,
    RESCHEDULE_REJECTED
}
