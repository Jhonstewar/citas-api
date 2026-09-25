package com.fcv.citas.domain.auth;

import java.time.Instant;

import com.fcv.citas.domain.user.User;

/**
 * Puerto de entrega del token de recuperacion al titular (HU-006 T-04). El envio real de correo
 * es opcional en el laboratorio (PRD §9): el adaptador actual no envia nada, y uno de correo
 * puede enchufarse despues sin tocar el caso de uso.
 *
 * <p>Ninguna implementacion puede registrar {@code rawToken} en el log (D27, PRD §8).</p>
 */
public interface PasswordResetNotifier {

    void deliver(User user, String rawToken, Instant expiresAt);
}
