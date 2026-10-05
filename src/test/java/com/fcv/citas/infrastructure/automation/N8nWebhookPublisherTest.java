package com.fcv.citas.infrastructure.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.application.appointment.AppointmentEvent;
import com.fcv.citas.application.appointment.AppointmentEventType;
import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.shared.Refs.PatientRef;
import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * HU-035 (CA-06, CA-07, CA-08): el adaptador contra un servidor HTTP local del JDK. Las esperas
 * entre reintentos se acortan por el constructor del paquete; el contrato de produccion
 * (3 intentos, 1 s, 2 s, 4 s) se fija en {@link #productionDefaultsAreTheContract()}.
 */
@ExtendWith(OutputCaptureExtension.class)
class N8nWebhookPublisherTest {

    private static final String SECRET = "s3cr3t-de-prueba-0001";
    private static final String EMAIL = "ana.perez@ejemplo.test";
    private static final String REASON = "Motivo confidencial del rechazo";
    private static final List<Duration> FAST = List.of(Duration.ofMillis(20), Duration.ofMillis(40));

    private HttpServer server;
    private ExecutorService executor;
    private ExecutorService serverExecutor;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile Function<Integer, Integer> statusByAttempt = attempt -> 200;
    private final ObjectMapper json = new ObjectMapper();

    record Received(String method, String secretHeader, String contentType, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook/token-en-la-ruta", this::handle);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor); // atiende en paralelo: un handler lento no oculta los reintentos
        server.start();
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        int attempt = calls.incrementAndGet();
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("X-Webhook-Secret"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        int status = statusByAttempt.apply(attempt);
        if (status < 0) {
            exchange.close(); // sin respuesta: error de red para el cliente
            return;
        }
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook/token-en-la-ruta";
    }

    private N8nWebhookPublisher publisher(List<Duration> waits) {
        return new N8nWebhookPublisher(url(), SECRET, executor, Duration.ofSeconds(2), Duration.ofSeconds(5), waits);
    }

    private void drain() throws InterruptedException {
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    static AppointmentEvent event(String reason) {
        AppointmentView view = new AppointmentView(57, "APPROVED", "Aprobada", LocalDate.of(2026, 10, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 30, new SiteRef(2, "ICV", "Clinica Ficticia Valle"),
                new PersonRef(9, "Dr. Carlos Ruiz"), new SpecialtyRef(4, "CARD", "Cardiologia", "SPECIALIZED", 30),
                null, LocalDateTime.of(2026, 10, 1, 8, 0),
                new PatientRef(3, "Ana Perez", "CC", "1234567890", EMAIL, "3000000000"), false);
        return new AppointmentEvent(UUID.fromString("0b9d2d52-6f0c-4b52-9d36-2f0f5f0f1a11"),
                AppointmentEventType.APPOINTMENT_REJECTED, 57,
                OffsetDateTime.of(2026, 10, 1, 10, 15, 0, 123_456_789, ZoneOffset.ofHours(-5)), view, reason);
    }

    // ------------------------------------------------------------------ reintentos (CA-08)

    @Test
    void twoBadGatewaysThenOkMakesThreeAttemptsAndSucceeds() throws Exception {
        statusByAttempt = attempt -> attempt < 3 ? 502 : 200;
        publisher(FAST).publish(event(null));
        drain();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aBadRequestIsNotRetried() throws Exception {
        statusByAttempt = attempt -> 400;
        publisher(FAST).publish(event(null));
        drain();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void anOkIsNotRetried() throws Exception {
        publisher(FAST).publish(event(null));
        drain();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aServerErrorAlwaysGivesUpAfterThreeAttempts(CapturedOutput output) throws Exception {
        statusByAttempt = attempt -> 500;
        publisher(FAST).publish(event(null));
        drain();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(output.getAll()).contains("0b9d2d52-6f0c-4b52-9d36-2f0f5f0f1a11").contains("500");
    }

    @Test
    void aNetworkErrorExhaustsThreeAttemptsWithoutThrowingToTheCaller() throws Exception {
        statusByAttempt = attempt -> -1;
        N8nWebhookPublisher publisher = publisher(FAST);
        assertThatCode(() -> publisher.publish(event(null))).doesNotThrowAnyException();
        drain();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aTimeoutIsRetriedAndAbandonedWithoutThrowing() throws Exception {
        server.removeContext("/webhook/token-en-la-ruta");
        server.createContext("/webhook/token-en-la-ruta", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        N8nWebhookPublisher publisher = new N8nWebhookPublisher(url(), SECRET, executor, Duration.ofSeconds(2),
                Duration.ofMillis(150), FAST);
        assertThatCode(() -> publisher.publish(event(null))).doesNotThrowAnyException();
        drain();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void publishingIsAsynchronousAndDoesNotWaitForTheRetryBackoff() throws Exception {
        statusByAttempt = attempt -> 502;
        N8nWebhookPublisher slow = publisher(List.of(Duration.ofMillis(600), Duration.ofMillis(600)));
        long start = System.nanoTime();
        slow.publish(event(null));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(elapsedMillis).isLessThan(400);
        drain();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aSaturatedExecutorDoesNotThrowToTheCaller() {
        ExecutorService full = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(1));
        full.shutdown(); // rechaza todo
        N8nWebhookPublisher publisher = new N8nWebhookPublisher(url(), SECRET, full, Duration.ofSeconds(2),
                Duration.ofSeconds(5), FAST);
        assertThatCode(() -> publisher.publish(event(null))).doesNotThrowAnyException();
        assertThat(calls.get()).isZero();
    }

    @Test
    void productionDefaultsAreTheContract() {
        assertThat(N8nWebhookPublisher.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(2));
        assertThat(N8nWebhookPublisher.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(N8nWebhookPublisher.PRODUCTION_WAITS)
                .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    // ------------------------------------------------------------------ cabecera y cuerpo (CA-06)

    @Test
    void sendsAPostWithTheSecretHeaderAndJsonContent() throws Exception {
        publisher(FAST).publish(event(null));
        drain();
        assertThat(received).hasSize(1);
        Received request = received.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.secretHeader()).isEqualTo(SECRET);
        assertThat(request.contentType()).startsWith("application/json");
    }

    @Test
    void withoutSecretNothingIsSent() throws Exception {
        new N8nWebhookPublisher(url(), "", executor, Duration.ofSeconds(2), Duration.ofSeconds(5), FAST)
                .publish(event(null));
        drain();
        assertThat(calls.get()).isZero();
    }

    @Test
    void theBodyHasExactlyTheKeysOfTheContract() throws Exception {
        publisher(FAST).publish(event("Sin cupo"));
        drain();
        JsonNode body = json.readTree(received.get(0).body());

        assertThat(fieldNames(body)).containsExactlyInAnyOrder("eventId", "eventType", "occurredAt", "appointmentId",
                "patient", "appointment", "reason");
        assertThat(fieldNames(body.get("patient"))).containsExactlyInAnyOrder("fullName", "email");
        assertThat(fieldNames(body.get("appointment"))).containsExactlyInAnyOrder("date", "startTime", "endTime",
                "site", "professional", "specialty");
        assertThat(fieldNames(body.get("appointment").get("site"))).containsExactlyInAnyOrder("code", "name");

        assertThat(body.get("eventId").asText()).isEqualTo("0b9d2d52-6f0c-4b52-9d36-2f0f5f0f1a11");
        assertThat(body.get("eventType").asText()).isEqualTo("APPOINTMENT_REJECTED");
        assertThat(body.get("occurredAt").asText()).isEqualTo("2026-10-01T10:15:00-05:00");
        assertThat(body.get("appointmentId").asLong()).isEqualTo(57);
        assertThat(body.get("patient").get("fullName").asText()).isEqualTo("Ana Perez");
        assertThat(body.get("patient").get("email").asText()).isEqualTo(EMAIL);
        assertThat(body.get("appointment").get("date").asText()).isEqualTo("2026-10-05");
        assertThat(body.get("appointment").get("startTime").asText()).isEqualTo("09:00");
        assertThat(body.get("appointment").get("endTime").asText()).isEqualTo("09:30");
        assertThat(body.get("appointment").get("site").get("code").asText()).isEqualTo("ICV");
        assertThat(body.get("appointment").get("site").get("name").asText()).isEqualTo("Clinica Ficticia Valle");
        assertThat(body.get("appointment").get("professional").asText()).isEqualTo("Dr. Carlos Ruiz");
        assertThat(body.get("appointment").get("specialty").asText()).isEqualTo("Cardiologia");
        assertThat(body.get("reason").asText()).isEqualTo("Sin cupo");
        // Nada de documento ni telefono (HU-035, reglas de negocio).
        assertThat(received.get(0).body()).doesNotContain("1234567890").doesNotContain("3000000000");
    }

    @Test
    void aMissingReasonIsSentAsNull() throws Exception {
        publisher(FAST).publish(event(null));
        drain();
        JsonNode body = json.readTree(received.get(0).body());
        assertThat(body.has("reason")).isTrue();
        assertThat(body.get("reason").isNull()).isTrue();
    }

    @Test
    void aReasonLongerThan300CharactersIsTruncated() throws Exception {
        publisher(FAST).publish(event("x".repeat(1000)));
        drain();
        String reason = json.readTree(received.get(0).body()).get("reason").asText();
        assertThat(reason).hasSize(300);
    }

    @Test
    void aReasonOfExactly300CharactersIsKept() throws Exception {
        publisher(FAST).publish(event("y".repeat(300)));
        drain();
        assertThat(json.readTree(received.get(0).body()).get("reason").asText()).hasSize(300);
    }

    // ------------------------------------------------------------------ log sin datos personales (CA-07)

    @Test
    void theLogNeverContainsEmailReasonSecretBodyOrUrl(CapturedOutput output) throws Exception {
        statusByAttempt = attempt -> attempt < 2 ? 502 : 200;
        publisher(FAST).publish(event(REASON));
        drain();
        // Segundo evento con error de red, para cubrir la rama de excepcion.
        executor = Executors.newSingleThreadExecutor();
        statusByAttempt = attempt -> -1;
        publisher(FAST).publish(event(REASON));
        drain();

        String log = output.getAll();
        assertThat(log).contains("0b9d2d52-6f0c-4b52-9d36-2f0f5f0f1a11").contains("APPOINTMENT_REJECTED");
        assertThat(log).doesNotContain(EMAIL).doesNotContain("ana.perez").doesNotContain(REASON)
                .doesNotContain("confidencial").doesNotContain(SECRET).doesNotContain("token-en-la-ruta")
                .doesNotContain("127.0.0.1:" + server.getAddress().getPort()).doesNotContain("\"fullName\"");
    }

    private static Set<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }
}
