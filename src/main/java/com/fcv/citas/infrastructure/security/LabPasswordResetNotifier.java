package com.fcv.citas.infrastructure.security;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.auth.PasswordResetNotifier;
import com.fcv.citas.domain.user.User;

/**
 * Adaptador de laboratorio del puerto de entrega: NO envia correo (PRD §9 lo deja fuera del
 * alcance obligatorio). En el laboratorio el token llega solo por la respuesta controlada de D27.
 *
 * <p>Registra que hubo una emision, con el id interno del usuario y la caducidad; nunca el token
 * ni el email (PRD §8). Un adaptador SMTP sustituiria a esta clase sin tocar el caso de uso.</p>
 */
@Component
class LabPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LabPasswordResetNotifier.class);

    @Override
    public void deliver(User user, String rawToken, Instant expiresAt) {
        log.info("Token de recuperacion emitido para el usuario id={} (caduca {}); sin envio de correo en el "
                + "laboratorio", user.id(), expiresAt);
    }
}
