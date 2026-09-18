package com.fcv.citas.infrastructure.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.auth.AccessTokenIssuer;
import com.fcv.citas.domain.auth.IssuedAccessToken;
import com.fcv.citas.domain.user.User;

/** Emite el access token HS256 con {@code sub}=id de usuario y claim {@code roles}. */
@Component
class NimbusAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    NimbusAccessTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(User user, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.accessTtl());
        List<String> roles = user.roles().stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(String.valueOf(user.id()))
                // `iat` y `exp` se serializan en segundos enteros: dos emisiones para el mismo
                // usuario dentro del mismo segundo tendrian la misma carga util y, con la misma
                // clave, la misma firma. El `jti` aleatorio es lo que garantiza que un token
                // renovado sea siempre distinto del que sustituye (HU-003 CA-01).
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                // Sin claim `email`: un JWT va firmado, no cifrado, y su carga util la lee
                // cualquiera que vea el token. Nadie lo consumia —el servidor identifica por
                // `sub` y el frontend solo mira `roles`—, asi que era dato personal expuesto
                // a cambio de nada (PRD seccion 8).
                .claim(JwtConfig.ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }
}
