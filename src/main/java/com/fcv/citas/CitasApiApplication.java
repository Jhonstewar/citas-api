package com.fcv.citas;

import java.util.TimeZone;

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
        applyDefaultTimeZone();
        SpringApplication.run(CitasApiApplication.class, args);
    }

    /**
     * Fija la zona horaria de la JVM para que fechas/horas de agenda y auditoria sean
     * consistentes con la operacion, independientemente del host o del contenedor.
     *
     * <p>Se llama en {@code main}, ANTES de arrancar Spring. Hasta S3 se hacia en un
     * {@code @PostConstruct}, cuando Hikari y Flyway ya habian abierto conexiones con la zona
     * anterior (UTC en el contenedor): Hibernate convertia {@code LocalTime} con una zona y lo leia
     * con otra, y las horas de agenda se desplazaban 5 h segun que contexto arrancara primero. Las
     * pruebas la fijan con {@code -Duser.timezone} (surefire, {@code pom.xml}).</p>
     */
    static void applyDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APP_TIME_ZONE));
    }
}
