package com.fcv.citas.infrastructure.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.fcv.citas.infrastructure.security.BcryptPasswordLimit;

/** Valida {@link BcryptPasswordLength} con la misma cuenta de bytes que usa el hasher. */
public class BcryptPasswordLengthValidator implements ConstraintValidator<BcryptPasswordLength, CharSequence> {

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || !BcryptPasswordLimit.exceeds(value);
    }
}
