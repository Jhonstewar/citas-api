package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HU-034 CA-08: la clave configurada y la cabecera recibida no aparecen en la salida de log, ni durante
 * el arranque del contexto ni durante peticiones con clave correcta, erronea, ausente o con metodo no GET.
 * Se sube el nivel de log de seguridad y web (con detalle de peticion) para que una fuga seria visible.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = { "logging.level.org.springframework.security=TRACE",
        "logging.level.org.springframework.web=TRACE", "spring.mvc.log-request-details=true" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutomationKeyNotLoggedIntegrationTest {

    private static final String CONFIGURED = "test-only-automation-key-0123456789abcdef";
    private static final String SENT_WRONG = "clave-enviada-erronea-ZZZ-987654321";
    private static final String URL = "/api/automation/appointments/upcoming";

    @Autowired
    private MockMvc mvc;

    @Test
    void neitherConfiguredNorSentKeyAppearsInLogs(CapturedOutput output) throws Exception {
        mvc.perform(get(URL).header("X-Automation-Key", CONFIGURED));
        mvc.perform(get(URL).header("X-Automation-Key", SENT_WRONG));
        mvc.perform(get(URL));
        mvc.perform(post(URL).header("X-Automation-Key", CONFIGURED));
        mvc.perform(post(URL).header("X-Automation-Key", SENT_WRONG));
        assertThat(output.getAll()).as("salida de log").isNotEmpty()
                .doesNotContain(CONFIGURED).doesNotContain(SENT_WRONG);
    }
}
