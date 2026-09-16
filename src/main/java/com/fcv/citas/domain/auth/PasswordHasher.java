package com.fcv.citas.domain.auth;

/** Puerto de hash adaptativo de contraseñas. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
