package com.fcv.citas.infrastructure.security;

import java.nio.charset.StandardCharsets;

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

    @Bean
    SecretKey jwtAccessSecretKey(JwtProperties properties) {
        return buildHmacKey(properties.accessSecret());
    }

    /**
     * TRAMPA 2: valida la longitud al construir la clave para que la aplicacion NO arranque con
     * un secreto debil. El mensaje nunca incluye el secreto.
     */
    static SecretKey buildHmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_ACCESS_SECRET no esta configurado");
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
