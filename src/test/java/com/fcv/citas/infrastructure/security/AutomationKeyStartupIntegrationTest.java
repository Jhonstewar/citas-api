package com.fcv.citas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.fcv.citas.CitasApiApplication;

/**
 * HU-034 CA-09 con la aplicacion REAL (CitasApiApplication, perfil test, base de pruebas), no solo el
 * record: una clave corta o con CHANGE_ME aborta el arranque sin imprimir el valor; vacia arranca.
 * Puerto 0 para no chocar con otros contextos. Cada arranque que triunfa se cierra.
 */
class AutomationKeyStartupIntegrationTest {

    /** La clave va como argumento de linea de comandos: precedencia sobre application-test.yml. */
    private static ConfigurableApplicationContext start(String key) {
        return new SpringApplicationBuilder(CitasApiApplication.class).profiles("test")
                .run("--server.port=0", "--app.automation.api-key=" + key);
    }

    private static void assertFailsWithoutEchoing(String key, String expectedFragment) {
        Throwable failure = null;
        ConfigurableApplicationContext started = null;
        try {
            started = start(key);
        } catch (RuntimeException t) {
            failure = t;
        } finally {
            if (started != null) {
                started.close();
            }
        }
        assertThat(failure).as("fallo de arranque").isNotNull();
        boolean mentionsKey = false;
        boolean validationCause = false;
        for (Throwable t = failure; t != null; t = t.getCause()) {
            String message = String.valueOf(t.getMessage());
            mentionsKey |= message.contains(key);
            validationCause |= t instanceof IllegalStateException && message.contains("AUTOMATION_API_KEY")
                    && message.contains(expectedFragment);
        }
        assertThat(validationCause).as("la causa debe ser la validacion de AutomationProperties").isTrue();
        assertThat(mentionsKey).as("el mensaje no debe contener el valor de la clave").isFalse();
    }

    @Test
    void shortKeyAbortsStartup() {
        assertFailsWithoutEchoing("demasiado-corta-123", "32 bytes");
    }

    @Test
    void changeMeKeyOfAtLeast32BytesAbortsStartup() {
        assertFailsWithoutEchoing("CHANGE_ME-pero-con-mas-de-treinta-y-dos-bytes", "CHANGE_ME");
    }

    @Test
    void emptyKeyStartsTheApplication() {
        try (ConfigurableApplicationContext ctx = start("")) {
            assertThat(ctx.isActive()).isTrue();
            assertThat(ctx.getBean(AutomationProperties.class).enabled()).isFalse();
        }
    }
}
