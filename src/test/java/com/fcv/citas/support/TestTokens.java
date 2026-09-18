package com.fcv.citas.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.fcv.citas.domain.user.Role;
import com.fcv.citas.infrastructure.security.JwtConfig;

/**
 * Emite access tokens validos para las pruebas de integracion, con la misma forma que
 * {@code NimbusAccessTokenIssuer}: {@code sub} = id de usuario y claim {@code roles}. Evita pasar
 * por el login en cada prueba de autorizacion.
 */
public final class TestTokens {

    private final JwtEncoder encoder;

    public TestTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String forUser(long userId, Role... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .claim(JwtConfig.ROLES_CLAIM, Arrays.stream(roles).map(Enum::name).sorted().toList())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public String bearer(long userId, Role... roles) {
        return "Bearer " + forUser(userId, roles);
    }
}
