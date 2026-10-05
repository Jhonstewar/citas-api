package com.fcv.citas.infrastructure.automation;

import com.fcv.citas.application.appointment.AppointmentEvent;
import com.fcv.citas.application.appointment.AppointmentEventPublisher;

/**
 * Adaptador nulo (HU-035, CA-05): no abre conexiones ni registra nada. Lo elige
 * {@link AppointmentEventPublisherConfig} cuando {@code N8N_WEBHOOK_WF002_URL} esta vacia.
 */
public class NoOpAppointmentEventPublisher implements AppointmentEventPublisher {

    @Override
    public void publish(AppointmentEvent event) {
        // Intencionalmente vacio.
    }
}
