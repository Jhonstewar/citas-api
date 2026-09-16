package com.fcv.citas;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la API de agendamiento de citas.
 *
 * <p>Es un adaptador primario del framework: vive en la raiz del paquete base para que el
 * component scan alcance unicamente {@code infrastructure}, donde estan los beans de Spring.
 * Los paquetes {@code domain} y {@code application} no contienen beans.</p>
 */
@SpringBootApplication
public class CitasApiApplication {

    /** Zona horaria operativa del laboratorio (sedes HIC e ICV, Santander - Colombia). */
    public static final String APP_TIME_ZONE = "America/Bogota";

    public static void main(String[] args) {
        SpringApplication.run(CitasApiApplication.class, args);
    }

    /**
     * Fija la zona horaria de la JVM para que fechas/horas de agenda y auditoria sean
     * consistentes con la operacion, independientemente del host o del contenedor.
     */
    @PostConstruct
    void applyDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APP_TIME_ZONE));
    }
}
