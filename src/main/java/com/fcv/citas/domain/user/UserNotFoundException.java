package com.fcv.citas.domain.user;

import com.fcv.citas.domain.shared.DomainException;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super("Usuario no encontrado");
    }
}
