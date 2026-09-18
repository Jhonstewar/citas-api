package com.fcv.citas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * TRAMPA 2: un secreto HMAC menor de 256 bits impide construir la clave Y ARRANCAR la aplicacion.
 *
 * <p>La verificacion independiente de S2 señalo que las pruebas solo llamaban a
 * {@link JwtConfig#buildHmacKey} directamente: comprobaban el metodo, no la consecuencia. Que el
 * metodo lance no demuestra que el contexto de Spring se niegue a levantar, que es lo que de
 * verdad protege al sistema. Aqui se arranca un contexto real con la configuracion JWT.</p>
 */
class JwtSecretValidationTest {

    private static final String WEAK_SECRET = "test-only-too-short-secret";
    private static final String VALID_SECRET = "test-only-0123456789abcdef-012345";

    /** Valor literal de {@code JWT_ACCESS_SECRET} en el {@code .env.example} de la raiz. */
    private static final String ROOT_ENV_EXAMPLE_PLACEHOLDER = "CHANGE_ME_ACCESS_SECRET_MIN_32_CHARS";
    /** Valor literal de {@code JWT_ACCESS_SECRET} en el {@code .env.example} de citas-api. */
    private static final String API_ENV_EXAMPLE_PLACEHOLDER = "CHANGE_ME_MIN_32_CHARS";

    /** Contexto minimo: solo JwtConfig y sus propiedades, sin base de datos ni web. */
    private static ApplicationContextRunner contextWith(String secret) {
        return new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(JwtConfig.class))
                .withBean(JwtProperties.class,
                        () -> new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7)));
    }

    /* ---------------------------------------------------------------------- */
    /* La aplicacion no arranca con un secreto invalido                       */
    /* ---------------------------------------------------------------------- */

    @Test
    void contextDoesNotStartWithShortSecret() {
        contextWith(WEAK_SECRET).run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits"));
    }

    @Test
    void startupFailureDoesNotEchoTheSecret() {
        contextWith(WEAK_SECRET).run(context -> {
            assertThat(context).hasFailed();
            // Ni el mensaje de la excepcion ni el de ninguna de sus causas puede filtrar el valor.
            Throwable cause = context.getStartupFailure();
            while (cause != null) {
                assertThat(String.valueOf(cause.getMessage())).doesNotContain(WEAK_SECRET);
                cause = cause.getCause();
            }
        });
    }

    @Test
    void contextDoesNotStartWithBlankSecret() {
        contextWith("   ").run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no esta configurado"));
    }

    @Test
    void contextDoesNotStartWithTheEnvExamplePlaceholder() {
        contextWith(ROOT_ENV_EXAMPLE_PLACEHOLDER).run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".env.example")
                .hasMessageContaining("CHANGE_ME")
                .hasMessageNotContaining(ROOT_ENV_EXAMPLE_PLACEHOLDER));
    }

    @Test
    void contextStartsWithA32ByteSecretAndPublishesTheJwtBeans() {
        contextWith(VALID_SECRET).run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(SecretKey.class)
                .hasSingleBean(JwtEncoder.class)
                .hasSingleBean(JwtDecoder.class));
    }

    /* ---------------------------------------------------------------------- */
    /* Validacion de la clave en aislamiento                                  */
    /* ---------------------------------------------------------------------- */

    @Test
    void rejectsShortSecretWithoutEchoingIt() {
        assertThatThrownBy(() -> JwtConfig.buildHmacKey(WEAK_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits")
                .hasMessageNotContaining(WEAK_SECRET);
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> JwtConfig.buildHmacKey("  ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsSecretOf32Bytes() {
        assertThat(JwtConfig.buildHmacKey(VALID_SECRET).getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void rejectsSecretOneByteShort() {
        // El borde exacto: 31 bytes se rechazan, 32 se aceptan (prueba anterior).
        String thirtyOneBytes = "a".repeat(31);
        assertThatThrownBy(() -> JwtConfig.buildHmacKey(thirtyOneBytes))
                .isInstanceOf(IllegalStateException.class);
    }

    /* ---------------------------------------------------------------------- */
    /* Los marcadores de .env.example no son secretos                         */
    /* ---------------------------------------------------------------------- */

    @Test
    void theRootPlaceholderWouldPassTheLengthCheckOnItsOwn() {
        // Por eso hace falta un control especifico: la longitud sola no lo detiene.
        assertThat(ROOT_ENV_EXAMPLE_PLACEHOLDER.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeGreaterThanOrEqualTo(JwtConfig.MIN_SECRET_BYTES);
    }

    @Test
    void rejectsTheRootEnvExamplePlaceholder() {
        assertThatThrownBy(() -> JwtConfig.buildHmacKey(ROOT_ENV_EXAMPLE_PLACEHOLDER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valor de ejemplo")
                .hasMessageNotContaining(ROOT_ENV_EXAMPLE_PLACEHOLDER);
    }

    @Test
    void rejectsTheApiEnvExamplePlaceholderAsAPlaceholderRatherThanAsTooShort() {
        // Mide 22 bytes, pero el mensaje util es "es el ejemplo", no "es corto": quien lo alargara
        // para cumplir la longitud seguiria usando un marcador publico.
        assertThatThrownBy(() -> JwtConfig.buildHmacKey(API_ENV_EXAMPLE_PLACEHOLDER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valor de ejemplo")
                .hasMessageNotContaining("256 bits");
    }

    @Test
    void rejectsAnyValueContainingTheMarkerRegardlessOfCase() {
        String padded = "mi-clave-" + "change_me" + "-0123456789abcdef-0123456789";
        assertThatThrownBy(() -> JwtConfig.buildHmacKey(padded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valor de ejemplo")
                .hasMessageNotContaining(padded);
    }
}
