package com.fcv.citas.infrastructure.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.fcv.citas.application.appointment.AppointmentEventPublisher;
import com.fcv.citas.infrastructure.security.N8nProperties;
import com.sun.net.httpserver.HttpServer;

/**
 * HU-035 (CA-05, CA-10): con la URL de WF-002 vacia el contexto tiene el adaptador nulo y no sale
 * ninguna peticion; con URL, el adaptador HTTP. Nunca hay dos beans del puerto.
 */
class AppointmentEventPublisherSelectionTest {

    @Configuration
    @EnableConfigurationProperties(N8nProperties.class)
    static class Props {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Props.class, AppointmentEventPublisherConfig.class);

    @Test
    void emptyUrlSelectsTheNoOpPublisherAndNothingIsSent() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            runner.withPropertyValues("app.n8n.secret=algo", "app.n8n.wf002-url=").run(context -> {
                assertThat(context).getBeans(AppointmentEventPublisher.class).hasSize(1);
                assertThat(context.getBean(AppointmentEventPublisher.class))
                        .isInstanceOf(NoOpAppointmentEventPublisher.class);
                context.getBean(AppointmentEventPublisher.class).publish(N8nWebhookPublisherTest.event(null));
            });
            Thread.sleep(200);
            assertThat(hits.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingPropertySelectsTheNoOpPublisher() {
        runner.run(context -> {
            assertThat(context).getBeans(AppointmentEventPublisher.class).hasSize(1);
            assertThat(context.getBean(AppointmentEventPublisher.class))
                    .isInstanceOf(NoOpAppointmentEventPublisher.class);
        });
    }

    @Test
    void aConfiguredUrlSelectsTheHttpPublisher() {
        runner.withPropertyValues("app.n8n.secret=algo", "app.n8n.wf002-url=https://n8n.ejemplo.test/webhook/abc")
                .run(context -> {
                    assertThat(context).getBeans(AppointmentEventPublisher.class).hasSize(1);
                    assertThat(context.getBean(AppointmentEventPublisher.class))
                            .isInstanceOf(N8nWebhookPublisher.class);
                });
    }
}
