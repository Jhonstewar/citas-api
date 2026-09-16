package com.fcv.citas.domain.auth;

/** Puerto que genera el valor opaco (alta entropia) del refresh token. */
public interface SecureTokenGenerator {

    String generate();
}
