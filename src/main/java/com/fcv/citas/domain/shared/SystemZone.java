package com.fcv.citas.domain.shared;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Zona horaria de referencia del sistema (INC-017): las sedes estan en Santander, Colombia. Las
 * fechas y horas de bloques y citas son locales de esta zona; "pasado" se decide aqui.
 */
public final class SystemZone {

    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private SystemZone() {
    }

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock.withZone(ZONE));
    }

    /** "Hoy" segun la zona del sistema, no la del servidor ni la de UTC. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
