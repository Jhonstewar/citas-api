package com.fcv.citas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * HU-034 (clave de automatizacion) y HU-035 (URLs de n8n): reglas de validacion al arrancar.
 * Se comprueban en el record y, sobre todo, con un contexto real enlazado desde propiedades,
 * que es lo que demuestra que la API se niega a levantar.
 */
class AutomationAndN8nPropertiesTest {

    private static final String VALID_KEY = "test-only-automation-key-0123456789abcdef";

    @EnableConfigurationProperties({ AutomationProperties.class, N8nProperties.class })
    static class Config {
    }

    private static ApplicationContextRunner runner(String... props) {
        return new ApplicationContextRunner().withUserConfiguration(Config.class).withPropertyValues(props);
    }

    /* ---------------- Regla 1: apiKey vacia = cadena cerrada, la API arranca ---------------- */

    @Test
    void emptyApiKeyIsAcceptedAndMeansClosedChain() {
        assertThat(new AutomationProperties("").enabled()).isFalse();
        assertThat(new AutomationProperties(null).enabled()).isFalse();
        assertThat(new AutomationProperties("   ").enabled()).isFalse();
        runner("app.automation.api-key=").run(ctx -> assertThat(ctx).hasNotFailed());
        runner().run(ctx -> assertThat(ctx).hasNotFailed());
    }

    /* ---------------- Regla 2: apiKey corta o CHANGE_ME = falla el arranque ---------------- */

    @Test
    void shortApiKeyFailsStartupWithoutEchoingIt() {
        String shortKey = "demasiado-corta-123";
        assertThatThrownBy(() -> new AutomationProperties(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(shortKey);
        runner("app.automation.api-key=" + shortKey).run(ctx -> {
            assertThat(ctx).hasFailed();
            Throwable t = ctx.getStartupFailure();
            while (t != null) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain(shortKey);
                t = t.getCause();
            }
        });
    }

    @Test
    void apiKeyWithChangeMeFailsStartup() {
        String placeholder = "change_me-pero-con-mas-de-treinta-y-dos-bytes";
        assertThatThrownBy(() -> new AutomationProperties(placeholder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHANGE_ME");
        runner("app.automation.api-key=" + placeholder).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void validApiKeyStartsAndEnablesTheChain() {
        assertThat(new AutomationProperties(VALID_KEY).enabled()).isTrue();
        runner("app.automation.api-key=" + VALID_KEY).run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void toStringDoesNotExposeTheKeyOrTheN8nSecret() {
        assertThat(new AutomationProperties(VALID_KEY).toString()).doesNotContain(VALID_KEY);
        assertThat(new N8nProperties("super-secreto-n8n", "", "", "").toString())
                .doesNotContain("super-secreto-n8n");
    }

    /* ---------------- Regla 3: URL de n8n no vacia sin https:// = falla el arranque ---------------- */

    @Test
    void emptyN8nUrlsMeanPublicationDisabledAndStart() {
        N8nProperties empty = new N8nProperties("", "", "", "");
        assertThat(empty.wf001Enabled()).isFalse();
        assertThat(empty.wf002Enabled()).isFalse();
        assertThat(empty.wf003Enabled()).isFalse();
        assertThatCode(() -> new N8nProperties(null, null, null, null)).doesNotThrowAnyException();
        runner().run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void httpsN8nUrlsAreAccepted() {
        N8nProperties p = new N8nProperties("s", "https://n8n.example.test/webhook/a", "", "");
        assertThat(p.wf001Enabled()).isTrue();
        assertThat(p.wf002Enabled()).isFalse();
    }

    @Test
    void nonHttpsN8nUrlFailsStartupForEachWorkflow() {
        assertThatThrownBy(() -> new N8nProperties("", "http://n8n.example.test/x", "", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("N8N_WEBHOOK_WF001_URL");
        assertThatThrownBy(() -> new N8nProperties("", "", "ftp://n8n.example.test/x", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("N8N_WEBHOOK_WF002_URL");
        assertThatThrownBy(() -> new N8nProperties("", "", "", "n8n.example.test/x"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("N8N_WEBHOOK_WF003_URL");
        runner("app.n8n.wf002-url=http://inseguro.test/x").run(ctx -> assertThat(ctx).hasFailed());
    }
}
