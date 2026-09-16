package com.fcv.citas.domain.auth;

import java.time.Instant;
import java.util.Optional;

/** Puerto de persistencia de refresh tokens hasheados. */
public interface RefreshTokenRepository {

    /** Inserta (id nulo) o actualiza el registro. Devuelve el token con id. */
    RefreshToken save(RefreshToken token);

    /** Busca por hash bloqueando el registro para evitar dos rotaciones concurrentes. */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /** Revoca todos los tokens aun no revocados de la familia. */
    void revokeFamily(String familyId, Instant now, String reason);
}
