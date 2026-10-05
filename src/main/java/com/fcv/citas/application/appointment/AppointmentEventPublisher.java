package com.fcv.citas.application.appointment;

/**
 * Puerto de salida (HU-035): publica un evento de cambio de estado hacia una automatizacion externa.
 * Se invoca SIEMPRE despues de confirmada la transaccion y es best-effort (D-F): un fallo del
 * publicador nunca cambia el resultado de la operacion. Sin Spring ni HTTP.
 */
public interface AppointmentEventPublisher {

    void publish(AppointmentEvent event);
}
