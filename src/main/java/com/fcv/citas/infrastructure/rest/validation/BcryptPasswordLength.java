package com.fcv.citas.infrastructure.rest.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import com.fcv.citas.infrastructure.security.BcryptPasswordLimit;

/**
 * La contraseña cabe en BCrypt: como maximo {@value BcryptPasswordLimit#MAX_BYTES} bytes en
 * UTF-8. Sustituye a {@code @Size(max = 72)}, que cuenta caracteres y dejaba pasar contraseñas
 * como 40 x ñ (80 bytes). {@code null} se considera valido: la obligatoriedad es cosa de
 * {@code @NotBlank}.
 *
 * <p>Solo es un tope tecnico, no una politica de complejidad (INC-001 sigue abierta).</p>
 */
@Documented
@Constraint(validatedBy = BcryptPasswordLengthValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface BcryptPasswordLength {

    String message() default "no debe superar " + BcryptPasswordLimit.MAX_BYTES
            + " bytes en UTF-8 (la ñ y las vocales con tilde ocupan 2 bytes; los emojis, 4)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
