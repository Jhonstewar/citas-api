package com.fcv.citas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.fcv.citas.domain.auth.IssuedAccessToken;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.domain.user.User;

/**
 * HU-003 CA-01 a nivel del emisor: "un access token nuevo, DISTINTO del anterior, cuya expiracion
 * es POSTERIOR a la del token que sustituye".
 *
 * <p>La verificacion independiente de S2 pregunto si dos tokens emitidos en el mismo segundo para
 * el mismo usuario podian salir identicos byte a byte, ya que {@code iat} y {@code exp} se
 * serializan en segundos enteros. Estas pruebas fijan la respuesta: la carga util coincide en todo
 * menos en el {@code jti}, que es aleatorio y basta para que el token sea distinto.</p>
 */
class NimbusAccessTokenIssuerTest {

    private static final SecretKey KEY = JwtConfig.buildHmacKey("test-only-0123456789abcdef-012345");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

    private final JwtConfig config = new JwtConfig();
    private final NimbusAccessTokenIssuer issuer = new NimbusAccessTokenIssuer(config.jwtEncoder(KEY),
            new JwtProperties("no-se-usa-aqui", ACCESS_TTL, Duration.ofDays(7)));
    private final JwtDecoder decoder = config.jwtDecoder(KEY);

    private final User user = new User(42L, "CC", "1098765432", "Ana", "Pérez", "ana@ejemplo.test", "300",
            "{bcrypt}no-importa", true, Set.of(Role.USER));

    @Test
    void twoTokensIssuedAtTheSameInstantForTheSameUserAreDistinct() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        IssuedAccessToken first = issuer.issue(user, now);
        IssuedAccessToken second = issuer.issue(user, now);

        assertThat(second.value()).isNotEqualTo(first.value());

        Jwt a = decoder.decode(first.value());
        Jwt b = decoder.decode(second.value());
        // Mismo emisor, sujeto, roles, iat y exp: lo UNICO que los separa es el jti.
        // `iss` como texto: Jwt#getIssuer() intenta convertirlo a URL y "citas-api" no lo es.
        assertThat(b.getClaimAsString("iss")).isEqualTo(a.getClaimAsString("iss")).isEqualTo(JwtConfig.ISSUER);
        assertThat(b.getSubject()).isEqualTo(a.getSubject());
        assertThat(b.getClaimAsStringList("roles")).isEqualTo(a.getClaimAsStringList("roles"));
        assertThat(b.getIssuedAt()).isEqualTo(a.getIssuedAt());
        assertThat(b.getExpiresAt()).isEqualTo(a.getExpiresAt());
        assertThat(a.getId()).isNotBlank();
        assertThat(b.getId()).isNotBlank().isNotEqualTo(a.getId());
    }

    @Test
    void expirationHasOneSecondGranularity() {
        // Dos emisiones dentro del mismo segundo comparten `exp`: por eso "expiracion posterior"
        // solo puede exigirse cuando entre ambas ha pasado al menos un segundo, y la prueba de
        // integracion de CA-01 controla el reloj en vez de depender de lo que tarde la peticion.
        // Se parte del reloj real porque el decoder rechaza un `exp` ya vencido.
        Instant second = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant early = second.plusMillis(100);
        Instant late = second.plusMillis(900);
        Instant next = second.plusSeconds(1);

        Instant expEarly = decoder.decode(issuer.issue(user, early).value()).getExpiresAt();
        Instant expLate = decoder.decode(issuer.issue(user, late).value()).getExpiresAt();
        Instant expNext = decoder.decode(issuer.issue(user, next).value()).getExpiresAt();

        assertThat(expLate).isEqualTo(expEarly).isEqualTo(second.plus(ACCESS_TTL));
        assertThat(expNext).isAfter(expLate);
    }
}
