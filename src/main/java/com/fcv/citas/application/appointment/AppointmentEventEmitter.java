package com.fcv.citas.application.appointment;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;

/**
 * HU-035: construye el evento y lo entrega al puerto capturando cualquier {@link RuntimeException}.
 * Solo se registra el TIPO de la excepcion: ni el mensaje, ni el cuerpo, ni datos del paciente.
 */
final class AppointmentEventEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentEventEmitter.class);

    private final AppointmentEventPublisher publisher;
    private final Clock clock;

    AppointmentEventEmitter(AppointmentEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    void emit(AppointmentEventType type, AppointmentView view, String reason) {
        try {
            publisher.publish(AppointmentEvent.of(type, view, reason, clock));
        } catch (RuntimeException e) {
            LOG.warn("No se pudo publicar el evento {} de la cita {}: {}", type, view.id(),
                    e.getClass().getSimpleName());
        }
    }
}
