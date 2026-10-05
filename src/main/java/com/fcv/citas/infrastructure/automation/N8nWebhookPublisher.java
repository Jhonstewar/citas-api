package com.fcv.citas.infrastructure.automation;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.application.appointment.AppointmentEvent;
import com.fcv.citas.application.appointment.AppointmentEventPublisher;
import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;

/**
 * HU-035: entrega el evento al webhook de WF-002 en n8n. <b>Best-effort</b> (D-F): se ejecuta en un
 * executor propio y acotado, no retiene el hilo de la peticion y, si se agotan los intentos, solo
 * deja un registro; nunca propaga una excepcion al llamador.
 *
 * <p>Politica: hasta 3 intentos con espera 1 s, 2 s (la espera de 4 s del plan solo aplicaria a un
 * cuarto intento, que no existe: {@link #PRODUCTION_WAITS} la conserva como tope declarado).
 * Se reintenta solo ante error de red/timeout o 5xx; un 2xx o 4xx es definitivo.</p>
 *
 * <p>Log: tipo de evento, {@code eventId} y codigo HTTP (o clase de la excepcion). Nunca cuerpo,
 * correo, motivo, secreto ni URL (puede llevar un token en la ruta).</p>
 */
public class N8nWebhookPublisher implements AppointmentEventPublisher, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(N8nWebhookPublisher.class);

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    /** Esperas del plan 1 s -> 2 s -> 4 s; con 3 intentos solo se usan las dos primeras. */
    static final List<Duration> PRODUCTION_WAITS = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(4));
    static final int MAX_ATTEMPTS = 3;
    static final int MAX_REASON_LENGTH = 300;
    static final String SECRET_HEADER = "X-Webhook-Secret";

    private static final int POOL_THREADS = 2;
    private static final int POOL_QUEUE = 100;
    private static final DateTimeFormatter OCCURRED_AT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final String url;
    private final String secret;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final List<Duration> waits;
    private final RestClient client;
    private final ObjectMapper json = new ObjectMapper();

    /** Produccion: URL y secreto de {@code app.n8n}, executor acotado propio, timeouts 2 s / 5 s. */
    public N8nWebhookPublisher(String url, String secret) {
        this(url, secret, boundedExecutor(), CONNECT_TIMEOUT, READ_TIMEOUT, PRODUCTION_WAITS, true);
    }

    /** Para pruebas: executor, timeouts y esperas inyectados. */
    N8nWebhookPublisher(String url, String secret, ExecutorService executor, Duration connectTimeout,
            Duration readTimeout, List<Duration> waits) {
        this(url, secret, executor, connectTimeout, readTimeout, waits, false);
    }

    private N8nWebhookPublisher(String url, String secret, ExecutorService executor, Duration connectTimeout,
            Duration readTimeout, List<Duration> waits, boolean ownsExecutor) {
        this.url = url;
        this.secret = secret == null ? "" : secret;
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
        this.waits = waits;
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void publish(AppointmentEvent event) {
        if (secret.isBlank()) {
            LOG.warn("Evento {} ({}) no publicado: N8N_WEBHOOK_SECRET vacio", event.type(), event.eventId());
            return;
        }
        String body;
        try {
            body = json.writeValueAsString(toPayload(event));
        } catch (JsonProcessingException e) {
            LOG.warn("Evento {} ({}) no publicado: no se pudo serializar ({})", event.type(), event.eventId(),
                    e.getClass().getSimpleName());
            return;
        }
        try {
            executor.execute(() -> deliver(event, body));
        } catch (RejectedExecutionException e) {
            LOG.warn("Evento {} ({}) descartado: cola de publicacion llena o cerrada", event.type(),
                    event.eventId());
        }
    }

    // ------------------------------------------------------------------ entrega

    private void deliver(AppointmentEvent event, String body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String outcome;
            boolean retry;
            try {
                int status = post(body);
                if (status >= 200 && status < 300) {
                    LOG.info("Evento {} ({}) entregado: HTTP {} (intento {})", event.type(), event.eventId(), status,
                            attempt);
                    return;
                }
                retry = status >= 500;
                outcome = "HTTP " + status;
            } catch (RuntimeException e) {
                retry = true;
                outcome = "error de red (" + e.getClass().getSimpleName() + ")";
            }
            if (!retry) {
                LOG.warn("Evento {} ({}) rechazado por el destino sin reintento: {}", event.type(), event.eventId(),
                        outcome);
                return;
            }
            if (attempt == MAX_ATTEMPTS) {
                LOG.warn("Evento {} ({}) abandonado tras {} intentos: {}", event.type(), event.eventId(), attempt,
                        outcome);
                return;
            }
            LOG.warn("Evento {} ({}) intento {} fallido: {}; se reintenta", event.type(), event.eventId(), attempt,
                    outcome);
            if (!pause(attempt)) {
                LOG.warn("Evento {} ({}) abandonado: la publicacion fue interrumpida", event.type(), event.eventId());
                return;
            }
        }
    }

    private int post(String body) {
        return client.post().uri(url).contentType(MediaType.APPLICATION_JSON).header(SECRET_HEADER, secret)
                .body(body).exchange((request, response) -> response.getStatusCode().value());
    }

    private boolean pause(int attempt) {
        Duration wait = waits.get(Math.min(attempt - 1, waits.size() - 1));
        try {
            Thread.sleep(wait.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ------------------------------------------------------------------ cuerpo del contrato 5.1

    private Map<String, Object> toPayload(AppointmentEvent event) {
        AppointmentView view = event.appointment();
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("fullName", view.patient() == null ? null : view.patient().fullName());
        patient.put("email", view.patient() == null ? null : view.patient().email());

        Map<String, Object> site = new LinkedHashMap<>();
        site.put("code", view.site().code());
        site.put("name", view.site().name());

        Map<String, Object> appointment = new LinkedHashMap<>();
        appointment.put("date", view.date().toString());
        appointment.put("startTime", view.startTime().format(HOUR_MINUTE));
        appointment.put("endTime", view.endTime().format(HOUR_MINUTE));
        appointment.put("site", site);
        appointment.put("professional", view.professional().fullName());
        appointment.put("specialty", view.specialty().name());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId().toString());
        payload.put("eventType", event.type().name());
        payload.put("occurredAt", formatInstant(event.occurredAt()));
        payload.put("appointmentId", event.appointmentId());
        payload.put("patient", patient);
        payload.put("appointment", appointment);
        payload.put("reason", truncate(event.reason()));
        return payload;
    }

    private static String formatInstant(OffsetDateTime instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).format(OCCURRED_AT);
    }

    static String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_REASON_LENGTH) {
            return reason;
        }
        int end = MAX_REASON_LENGTH;
        if (Character.isHighSurrogate(reason.charAt(end - 1))) {
            end--; // no partir un par sustituto
        }
        return reason.substring(0, end);
    }

    // ------------------------------------------------------------------ ciclo de vida

    private static ExecutorService boundedExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(POOL_THREADS, POOL_THREADS, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(POOL_QUEUE), runnable -> {
                    Thread thread = new Thread(runnable, "n8n-webhook-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void close() {
        if (ownsExecutor) {
            executor.shutdown();
        }
    }
}
