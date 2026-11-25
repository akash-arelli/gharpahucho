package com.gharpahucho.integration.sarvam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gharpahucho.config.AppConfig;
import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Client for the documented Sarvam AI REST APIs (https://docs.sarvam.ai):
 * <ul>
 *   <li>{@code POST /v2/chat/completions}: models {@code sarvam-105b} / {@code sarvam-105b-conversations}</li>
 *   <li>{@code POST /speech-to-text}: model {@code saaras:v4}, multipart upload, up to 30 seconds of audio</li>
 * </ul>
 * Authentication uses the {@code api-subscription-key} header. The key only ever lives in the backend environment.
 * Every failure becomes a {@link SarvamUnavailableException}; success is only reported when the API really answered.
 */
@Slf4j
@Component
public class SarvamClient {

    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String c) {
            return new ChatMessage("system", c);
        }

        public static ChatMessage user(String c) {
            return new ChatMessage("user", c);
        }
    }

    public record Transcript(String text, String languageCode, String requestId) {
    }

    private final GharPahuchoProperties.Sarvam props;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final IntegrationStatusRegistry registry;
    private final MeterRegistry meters;

    public SarvamClient(GharPahuchoProperties properties, ObjectMapper mapper, IntegrationStatusRegistry registry, MeterRegistry meters) {
        this.props = properties.getSarvam();
        this.mapper = mapper;
        this.registry = registry;
        this.meters = meters;
        this.http = AppConfig.restClient(props.getBaseUrl(), props.getTimeoutSeconds()).build();
        registry.register(IntegrationStatusRegistry.SARVAM, props.isConfigured(),
                props.isConfigured() ? "Chat: " + props.getChatModel() + ", speech-to-text: " + props.getSttModel()
                        : "Set SARVAM_API_KEY to enable. AI features fall back to deterministic parsing.");
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    /**
     * @param jsonMode asks the API for a guaranteed-valid JSON object ({@code response_format: json_object})
     */
    public String chat(List<ChatMessage> messages, double temperature, int maxTokens, boolean jsonMode, String purpose) {
        requireConfigured();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getChatModel());
        ArrayNode arr = body.putArray("messages");
        for (ChatMessage m : messages) {
            arr.addObject().put("role", m.role()).put("content", m.content());
        }
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        // Reasoning is not needed for extraction/short replies and would add seconds of latency and cost.
        body.put("reasoning_effort", "none");
        if (jsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }
        Timer.Sample sample = Timer.start(meters);
        try {
            JsonNode res = http.post()
                    .uri("/v2/chat/completions")
                    .header("api-subscription-key", props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = res == null ? null : res.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Sarvam returned an empty completion");
            }
            registry.success(IntegrationStatusRegistry.SARVAM);
            log.info("event=sarvam_chat_ok purpose={} model={}", purpose, props.getChatModel());
            return content;
        } catch (RestClientResponseException e) {
            String msg = "Sarvam chat HTTP " + e.getStatusCode().value() + " (" + purpose + ")";
            registry.failure(IntegrationStatusRegistry.SARVAM, msg);
            log.warn("event=sarvam_chat_failed purpose={} status={}", purpose, e.getStatusCode().value());
            throw new SarvamUnavailableException(msg, e);
        } catch (SarvamUnavailableException e) {
            throw e;
        } catch (Exception e) {
            String msg = "Sarvam chat failed (" + purpose + "): " + e.getClass().getSimpleName();
            registry.failure(IntegrationStatusRegistry.SARVAM, msg);
            log.warn("event=sarvam_chat_failed purpose={} error={}", purpose, e.getMessage());
            throw new SarvamUnavailableException(msg, e);
        } finally {
            sample.stop(meters.timer("gharpahucho.sarvam.latency", "operation", "chat"));
        }
    }

    /**
     * Speech to text. {@code languageCode} is a BCP-47 code such as {@code te-IN}, or {@code unknown} to auto-detect.
     */
    public Transcript transcribe(byte[] audio, String filename, String languageCode) {
        requireConfigured();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("model", props.getSttModel());
        form.add("mode", "codemix");
        form.add("language_code", languageCode == null || languageCode.isBlank() ? "unknown" : languageCode);
        Timer.Sample sample = Timer.start(meters);
        try {
            JsonNode res = http.post()
                    .uri("/speech-to-text")
                    .header("api-subscription-key", props.getApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String text = res == null ? null : res.path("transcript").asText(null);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Sarvam returned an empty transcript");
            }
            registry.success(IntegrationStatusRegistry.SARVAM);
            log.info("event=sarvam_stt_ok model={}", props.getSttModel());
            return new Transcript(text.trim(), res.path("language_code").asText(null), res.path("request_id").asText(null));
        } catch (RestClientResponseException e) {
            String msg = "Sarvam speech-to-text HTTP " + e.getStatusCode().value();
            registry.failure(IntegrationStatusRegistry.SARVAM, msg);
            log.warn("event=sarvam_stt_failed status={}", e.getStatusCode().value());
            throw new SarvamUnavailableException(msg, e);
        } catch (Exception e) {
            String msg = "Sarvam speech-to-text failed: " + e.getClass().getSimpleName();
            registry.failure(IntegrationStatusRegistry.SARVAM, msg);
            log.warn("event=sarvam_stt_failed error={}", e.getMessage());
            throw new SarvamUnavailableException(msg, e);
        } finally {
            sample.stop(meters.timer("gharpahucho.sarvam.latency", "operation", "speech_to_text"));
        }
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new SarvamUnavailableException("Sarvam AI is not configured (SARVAM_API_KEY is empty)", true);
        }
    }
}
