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

import com.fcv.citas.domain.auth.PasswordPolicy;

/**
 * La contraseña cumple la politica D29 de {@link PasswordPolicy}: minimo 8 caracteres, al menos
 * una letra Unicode y un digito, y como maximo 72 bytes UTF-8. Se usa en TODA entrada que fija una
 * contraseña —registro, restablecimiento y alta de profesional—, nunca en el login.
 *
 * <p>El mensaje del error es el del primer incumplimiento, en español y sin la contraseña.
 * {@code null} y el texto en blanco se consideran validos: la obligatoriedad es cosa de
 * {@code @NotBlank}, y asi el campo vacio da un unico error.</p>
 *
 * <p>Sustituye a {@code @BcryptPasswordLength}, que solo comprobaba el tope de 72 bytes.</p>
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface PasswordPolicyCompliant {

    String message() default "no cumple la política de contraseña";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
