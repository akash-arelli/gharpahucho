package com.gharpahucho.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.integration.nudgebee.NudgeBeeEventPublisher;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Verifies the real HTTP contract against NudgeBee's documented Event Webhook, using a local stand-in server. */
class NudgeBeeEventPublisherTest {

    record Received(String method, String path, String auth, String contentType, String body) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;
    private IntegrationStatusRegistry registry;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/webhooks/event", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestHeaders().getFirst("Authorization"),
                    ex.getRequestHeaders().getFirst("Content-Type"), body));
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        registry = new IntegrationStatusRegistry(new SimpleMeterRegistry());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private NudgeBeeEventPublisher publisher(boolean configured) {
        GharPahuchoProperties props = new GharPahuchoProperties();
        if (configured) {
            props.getNudgebee().setEventWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/event");
            props.getNudgebee().setWebhookToken("secret-token");
        }
        return new NudgeBeeEventPublisher(props, registry, mapper);
    }

    @Test
    void sendsNothingAndClaimsNothingWhenNotConfigured() {
        NudgeBeeEventPublisher p = publisher(false);
        p.fire("fp:1", "rule", "Title", "desc", "high", "svc", "service", Map.of(), false);
        assertThat(received).isEmpty();
        assertThat(p.status().webhookConfigured()).isFalse();
        assertThat(p.status().eventsSent()).isZero();
        assertThat(p.sendTest(false).sent()).isFalse();
        assertThat(registry.snapshot(3)).anyMatch(s -> s.name().equals(IntegrationStatusRegistry.NUDGEBEE) && s.status().equals("NOT_CONFIGURED"));
    }

    @Test
    void firingEventFollowsTheDocumentedPayloadAndUsesABearerToken() throws Exception {
        NudgeBeeEventPublisher p = publisher(true);
        NudgeBeeEventPublisher.SendResult r = p.sendTest(false);
        assertThat(r.sent()).isTrue();

        Received req = received.get(0);
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.auth()).isEqualTo("Bearer secret-token");
        assertThat(req.contentType()).startsWith("application/json");
        JsonNode j = mapper.readTree(req.body());
        for (String required : List.of("id", "fingerprint", "status", "title", "rule_name")) {
            assertThat(j.path(required).asText()).as(required).isNotBlank();
        }
        assertThat(j.path("status").asText()).isEqualTo("firing");
        assertThat(j.path("severity").asText()).isIn("critical", "high", "medium", "low", "info");
        assertThat(j.path("starts_at").asText()).endsWith("Z");
        assertThat(j.path("labels").isObject()).isTrue();
        j.path("labels").forEach(v -> assertThat(v.isTextual()).isTrue()); // labels must be strings
        j.path("labels").fieldNames().forEachRemaining(k -> assertThat(k).doesNotStartWith("nb_"));
        assertThat(p.status().eventsSent()).isEqualTo(1);
    }

    @Test
    void resolveReusesTheSameIdFingerprintAndLabels() throws Exception {
        NudgeBeeEventPublisher p = publisher(true);
        assertThat(p.sendTest(false).sent()).isTrue();
        assertThat(p.sendTest(true).sent()).isTrue();

        JsonNode firing = mapper.readTree(received.get(0).body());
        JsonNode resolved = mapper.readTree(received.get(1).body());
        assertThat(resolved.path("status").asText()).isEqualTo("resolved");
        assertThat(resolved.path("id").asText()).isEqualTo(firing.path("id").asText());
        assertThat(resolved.path("fingerprint").asText()).isEqualTo(firing.path("fingerprint").asText());
        assertThat(resolved.path("labels")).isEqualTo(firing.path("labels"));
        assertThat(resolved.path("ends_at").asText()).isNotBlank();
    }

    @Test
    void repeatedFiringOfTheSameProblemIsDeduplicated() {
        NudgeBeeEventPublisher p = publisher(true);
        for (int i = 0; i < 5; i++) {
            p.fire("dep:sarvam", "dependency_unavailable", "Sarvam is failing", "d", "high", "Sarvam AI", "dependency", Map.of(), false);
        }
        await().atMost(Duration.ofSeconds(5)).until(() -> received.size() >= 1);
        assertThat(received).hasSize(1);
        assertThat(p.isOpen("dep:sarvam")).isTrue();

        p.resolve("dep:sarvam");
        await().atMost(Duration.ofSeconds(5)).until(() -> received.size() >= 2);
        assertThat(p.isOpen("dep:sarvam")).isFalse();
    }

    @Test
    void aRejectedEventIsReportedHonestlyAndRetriedLater() {
        status.set(400);
        NudgeBeeEventPublisher p = publisher(true);
        NudgeBeeEventPublisher.SendResult r = p.sendTest(false);
        assertThat(r.sent()).isFalse();
        assertThat(r.message()).contains("400");
        assertThat(p.status().eventsFailed()).isEqualTo(1);
        assertThat(p.status().eventsSent()).isZero();
        assertThat(p.status().lastEventResult()).startsWith("FAILED");
        assertThat(registry.consecutiveFailures(IntegrationStatusRegistry.NUDGEBEE)).isEqualTo(1);

        // A failed delivery must not be remembered as "open", so the next occurrence tries again.
        status.set(200);
        assertThat(p.sendTest(false).sent()).isTrue();
    }
}
