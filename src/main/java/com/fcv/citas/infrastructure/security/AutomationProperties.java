package com.fcv.citas.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clave de la cadena de automatizacion (HU-034). Llega por {@code AUTOMATION_API_KEY} y nunca se
 * registra en log.
 *
 * <ul>
 *   <li>Vacia: la cadena de automatizacion queda CERRADA (todo 401) y la API arranca.</li>
 *   <li>No vacia con menos de {@value #MIN_KEY_BYTES} bytes o con {@code CHANGE_ME}: el arranque
 *       falla. Los mensajes nunca incluyen el valor.</li>
 * </ul>
 *
 * @param apiKey valor de {@code AUTOMATION_API_KEY}; se normaliza {@code null} a cadena vacia.
 */
@ConfigurationProperties(prefix = "app.automation")
public record AutomationProperties(String apiKey) {

    /** Misma exigencia que el secreto JWT: al menos 256 bits. */
    static final int MIN_KEY_BYTES = 32;

    static final String PLACEHOLDER_MARKER = "CHANGE_ME";

    public AutomationProperties {
        apiKey = apiKey == null ? "" : apiKey;
        if (!apiKey.isBlank()) {
            if (apiKey.toUpperCase(Locale.ROOT).contains(PLACEHOLDER_MARKER)) {
                throw new IllegalStateException("AUTOMATION_API_KEY conserva el valor de ejemplo (contiene "
                        + PLACEHOLDER_MARKER + "); sustituyala por un secreto aleatorio propio de al menos "
                        + MIN_KEY_BYTES + " bytes o dejela vacia para cerrar la cadena de automatizacion");
            }
            if (apiKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
                throw new IllegalStateException("AUTOMATION_API_KEY debe tener al menos " + MIN_KEY_BYTES
                        + " bytes o estar vacia (cadena de automatizacion cerrada)");
            }
        }
    }

    /** {@code true} si hay clave valida; con {@code false} la cadena rechaza todo con 401. */
    public boolean enabled() {
        return !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "AutomationProperties[apiKey=***]";
    }
}
