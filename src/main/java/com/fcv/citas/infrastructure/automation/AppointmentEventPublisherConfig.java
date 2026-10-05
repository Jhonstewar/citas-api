package com.fcv.citas.infrastructure.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fcv.citas.application.appointment.AppointmentEventPublisher;
import com.fcv.citas.infrastructure.security.N8nProperties;

/**
 * HU-035 (CA-05): un unico bean del puerto {@link AppointmentEventPublisher}. Con
 * {@code N8N_WEBHOOK_WF002_URL} vacia es el adaptador nulo (el laboratorio y las pruebas existentes
 * no cambian); con URL, el adaptador HTTP hacia n8n. Al ser un solo metodo {@code @Bean} nunca
 * pueden coexistir dos.
 *
 * <p>El executor acotado vive dentro del adaptador y no se declara como bean {@code Executor}: un bean
 * de ese tipo desactivaria el {@code applicationTaskExecutor} de Spring Boot.</p>
 */
@Configuration
class AppointmentEventPublisherConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentEventPublisherConfig.class);

    @Bean
    AppointmentEventPublisher appointmentEventPublisher(N8nProperties properties) {
        if (!properties.wf002Enabled()) {
            return new NoOpAppointmentEventPublisher();
        }
        LOG.info("Publicacion de eventos de cita hacia n8n (WF-002) activada");
        return new N8nWebhookPublisher(properties.wf002Url(), properties.secret());
    }
}
