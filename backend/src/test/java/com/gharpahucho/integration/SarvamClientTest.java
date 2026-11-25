package com.gharpahucho.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.integration.sarvam.SarvamClient;
import com.gharpahucho.integration.sarvam.SarvamUnavailableException;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SarvamClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> chatBody = new AtomicReference<>();
    private final AtomicReference<String> chatKey = new AtomicReference<>();
    private final AtomicReference<String> sttContentType = new AtomicReference<>();
    private HttpServer server;
    private IntegrationStatusRegistry registry;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/chat/completions", ex -> {
            chatBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            chatKey.set(ex.getRequestHeaders().getFirst("api-subscription-key"));
            reply(ex, "{\"choices\":[{\"message\":{\"content\":\"{\\\"destination_city\\\":\\\"Hyderabad\\\"}\"}}]}");
        });
        server.createContext("/speech-to-text", ex -> {
            sttContentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            ex.getRequestBody().readAllBytes();
            reply(ex, "{\"transcript\":\"Naaku Hyderabad vellali\",\"language_code\":\"te-IN\",\"request_id\":\"r1\"}");
        });
        server.start();
        registry = new IntegrationStatusRegistry(new SimpleMeterRegistry());
    }

    private void reply(com.sun.net.httpserver.HttpExchange ex, String json) throws java.io.IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status.get(), out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SarvamClient client(String key) {
        GharPahuchoProperties props = new GharPahuchoProperties();
        props.getSarvam().setApiKey(key);
        props.getSarvam().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getSarvam().setTimeoutSeconds(5);
        return new SarvamClient(props, mapper, registry, new SimpleMeterRegistry());
    }

    @Test
    void chatSendsKeyInHeaderAndRequestsJsonWithoutReasoning() throws Exception {
        String out = client("test-key").chat(List.of(SarvamClient.ChatMessage.system("s"), SarvamClient.ChatMessage.user("u")), 0.0, 100, true, "test");
        assertThat(mapper.readTree(out).path("destination_city").asText()).isEqualTo("Hyderabad");
        assertThat(chatKey.get()).isEqualTo("test-key");
        JsonNode body = mapper.readTree(chatBody.get());
        assertThat(body.path("model").asText()).isEqualTo("sarvam-105b");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
        assertThat(body.path("messages")).hasSize(2);
        assertThat(registry.snapshot(3)).anyMatch(s -> s.name().equals(IntegrationStatusRegistry.SARVAM) && s.status().equals("UP"));
    }

    @Test
    void transcribeSendsMultipartAndReturnsTheDetectedLanguage() {
        SarvamClient.Transcript t = client("k").transcribe(new byte[]{1, 2, 3}, "voice.webm", "unknown");
        assertThat(t.text()).isEqualTo("Naaku Hyderabad vellali");
        assertThat(t.languageCode()).isEqualTo("te-IN");
        assertThat(sttContentType.get()).startsWith("multipart/form-data");
    }

    @Test
    void serverErrorsBecomeSarvamUnavailableAndAreCountedForMonitoring() {
        status.set(500);
        SarvamClient c = client("k");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> c.chat(List.of(SarvamClient.ChatMessage.user("u")), 0, 10, false, "test"))
                    .isInstanceOf(SarvamUnavailableException.class);
        }
        assertThat(registry.consecutiveFailures(IntegrationStatusRegistry.SARVAM)).isEqualTo(3);
        assertThat(registry.snapshot(3)).anyMatch(s -> s.name().equals(IntegrationStatusRegistry.SARVAM) && s.status().equals("DOWN"));
    }

    @Test
    void withoutAKeyNothingIsCalledAndNothingIsFaked() {
        SarvamClient c = client("");
        assertThat(c.isConfigured()).isFalse();
        assertThatThrownBy(() -> c.chat(List.of(SarvamClient.ChatMessage.user("u")), 0, 10, false, "test"))
                .isInstanceOfSatisfying(SarvamUnavailableException.class, e -> assertThat(e.isNotConfigured()).isTrue());
        assertThat(chatBody.get()).isNull();
    }
}
