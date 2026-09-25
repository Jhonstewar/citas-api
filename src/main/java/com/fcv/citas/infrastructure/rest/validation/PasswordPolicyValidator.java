package com.fcv.citas.infrastructure.rest.validation;

import java.util.Optional;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.fcv.citas.domain.auth.PasswordPolicy;

/** Valida {@link PasswordPolicyCompliant} delegando en la regla del dominio, sin duplicarla. */
public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicyCompliant, CharSequence> {

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        Optional<String> violation = PasswordPolicy.violation(value == null ? null : value.toString());
        if (violation.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(escapeTemplate(violation.get())).addConstraintViolation();
        return false;
    }

    /** El mensaje es texto fijo, no una plantilla: se neutralizan los caracteres de interpolacion. */
    private static String escapeTemplate(String message) {
        return message.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
