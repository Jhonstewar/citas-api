package com.fcv.citas.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Beans JWT propios (DEC-001). Con HMAC ninguna propiedad
 * {@code spring.security.oauth2.resourceserver.jwt.*} aplica.
 */
@Configuration
public class JwtConfig {

    public static final String ISSUER = "citas-api";
    public static final String ROLES_CLAIM = "roles";

    /** RFC 7518 §3.2: HS256 exige una clave de al menos 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    /** Marcador de los valores de ejemplo de {@code .env.example}: nunca es un secreto real. */
    static final String PLACEHOLDER_MARKER = "CHANGE_ME";

    @Bean
    SecretKey jwtAccessSecretKey(JwtProperties properties) {
        return buildHmacKey(properties.accessSecret());
    }

    /**
     * TRAMPA 2: valida el secreto al construir la clave para que la aplicacion NO arranque con
     * uno debil. El mensaje nunca incluye el secreto.
     */
    static SecretKey buildHmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_ACCESS_SECRET no esta configurado");
        }
        // Va antes que el control de longitud porque lo complementa: el .env.example de la raiz
        // trae un marcador de 36 bytes que SI lo supera. Quien copiara el ejemplo sin tocarlo
        // firmaria los tokens con una clave publicada en el repositorio, y cualquiera podria
        // forjar un access token con el rol que quisiera. Sin distinguir mayusculas.
        if (secret.toUpperCase(Locale.ROOT).contains(PLACEHOLDER_MARKER)) {
            throw new IllegalStateException("JWT_ACCESS_SECRET conserva el valor de ejemplo de .env.example "
                    + "(contiene " + PLACEHOLDER_MARKER + "); sustituyalo por un secreto aleatorio propio de al "
                    + "menos " + MIN_SECRET_BYTES + " bytes");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_ACCESS_SECRET debe tener al menos " + MIN_SECRET_BYTES
                    + " bytes (256 bits) para HS256; tiene " + bytes.length);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** TRAMPA 1: {@code NimbusJwtEncoder.withSecretKey} no existe en Spring Security 6.5.x. */
    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtAccessSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtAccessSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtAccessSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtAccessSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new JwtIssuerValidator(ISSUER)));
        return decoder;
    }

    /** TRAMPA 3: claim {@code roles} con prefijo {@code ROLE_} para que {@code hasRole} funcione. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
