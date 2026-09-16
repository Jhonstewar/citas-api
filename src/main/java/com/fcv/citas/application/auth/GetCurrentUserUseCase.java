package com.fcv.citas.application.auth;

import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserNotFoundException;
import com.fcv.citas.domain.user.UserRepository;

/** Devuelve los datos basicos del usuario autenticado. */
public class GetCurrentUserUseCase {

    private final UserRepository users;

    public GetCurrentUserUseCase(UserRepository users) {
        this.users = users;
    }

    public User get(long userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
