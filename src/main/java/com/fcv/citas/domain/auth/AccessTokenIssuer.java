package com.fcv.citas.domain.auth;

import java.time.Instant;

import com.fcv.citas.domain.user.User;

/** Puerto de emision del access token (JWT de corta duracion con identidad y roles). */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user, Instant issuedAt);
}
