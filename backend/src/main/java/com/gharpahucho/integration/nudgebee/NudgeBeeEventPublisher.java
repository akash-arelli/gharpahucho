package com.gharpahucho.integration.nudgebee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gharpahucho.config.AppConfig;
import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.dto.OpsDtos.NudgeBeeStatus;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sends operational events to NudgeBee using its documented <em>Event Webhook</em> contract
 * (POST /api/webhooks/event, JSON body with id / fingerprint / status / title / rule_name, Bearer token).
 *
 * <ul>
 *   <li>Nothing is sent, and nothing is claimed to be sent, until an Event Webhook URL is configured.</li>
 *   <li>Each problem has a stable fingerprint; while it is open, repeats are de-duplicated, and a later "resolved"
 *       carries the same id and labels so NudgeBee closes the same event (as documented).</li>
 *   <li>Sending happens on a background thread, so a slow webhook can never delay a user request.</li>
 * </ul>
 */
@Component
public class NudgeBeeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NudgeBeeEventPublisher.class);

    public static final String TEST_FINGERPRINT = "gharpahucho:test-event";

    /** One alert that is currently firing in NudgeBee. */
    public record OpenEvent(String id, String fingerprint, String ruleName, String title, Map<String, String> labels,
                            Instant startedAt, Instant lastSeen, boolean autoResolve) {
    }

    public record SendResult(boolean sent, String message) {
    }

    private final GharPahuchoProperties.NudgeBee props;
    private final IntegrationStatusRegistry registry;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nudgebee-publisher");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, OpenEvent> open = new ConcurrentHashMap<>();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong suppressed = new AtomicLong();
    private final AtomicReference<Instant> lastEventAt = new AtomicReference<>();
    private final AtomicReference<String> lastResult = new AtomicReference<>("No events sent yet");

    public NudgeBeeEventPublisher(GharPahuchoProperties properties, IntegrationStatusRegistry registry, ObjectMapper mapper) {
        this.props = properties.getNudgebee();
        this.registry = registry;
        this.mapper = mapper;
        this.http = AppConfig.restClient(null, 8).build();
        registry.register(IntegrationStatusRegistry.NUDGEBEE, props.isWebhookConfigured(),
                props.isWebhookConfigured() ? "Event Webhook configured; events are POSTed with a Bearer token"
                        : "Set NUDGEBEE_EVENT_WEBHOOK_URL (and NUDGEBEE_WEBHOOK_TOKEN) to forward events. Until then events are only logged.");
    }

    public boolean isConfigured() {
        return props.isWebhookConfigured();
    }

    /** Raises (or refreshes) an alert. Safe to call repeatedly: an already-open fingerprint is not re-sent. */
    public void fire(String fingerprint, String ruleName, String title, String description, String severity,
                     String subjectName, String subjectType, Map<String, String> extraLabels, boolean autoResolve) {
        OpenEvent existing = open.get(fingerprint);
        if (existing != null) {
            open.put(fingerprint, new OpenEvent(existing.id(), existing.fingerprint(), existing.ruleName(), existing.title(), existing.labels(),
                    existing.startedAt(), Instant.now(), existing.autoResolve()));
            suppressed.incrementAndGet();
            return;
        }
        Map<String, String> labels = new LinkedHashMap<>(baseLabels());
        if (extraLabels != null) {
            labels.putAll(extraLabels);
        }
        OpenEvent ev = new OpenEvent("gp-" + UUID.randomUUID().toString().substring(0, 12), fingerprint, ruleName, title, labels,
                Instant.now(), Instant.now(), autoResolve);
        open.put(fingerprint, ev);
        log.warn("event=ops_alert_firing fingerprint={} rule={} severity={} title=\"{}\"", fingerprint, ruleName, severity, title);
        if (!isConfigured()) {
            lastResult.set("Webhook not configured: alert logged locally only");
            return;
        }
        ObjectNode body = payload(ev, "firing", severity, description, subjectName, subjectType, null);
        executor.execute(() -> {
            SendResult r = post(body);
            if (!r.sent()) {
                open.remove(fingerprint); // not delivered: allow the next occurrence to try again
            }
        });
    }

    /** Closes a previously fired alert (same id and labels, status resolved), as NudgeBee documents. */
    public void resolve(String fingerprint) {
        OpenEvent ev = open.remove(fingerprint);
        if (ev == null) {
            return;
        }
        log.info("event=ops_alert_resolved fingerprint={}", fingerprint);
        if (!isConfigured()) {
            return;
        }
        ObjectNode body = payload(ev, "resolved", "medium", null, null, null, Instant.now());
        executor.execute(() -> post(body));
    }

    /** Synchronous variant used by the "send a test event" button so the UI can show the real HTTP outcome. */
    public SendResult sendTest(boolean resolve) {
        if (!isConfigured()) {
            return new SendResult(false, "NudgeBee Event Webhook is not configured (NUDGEBEE_EVENT_WEBHOOK_URL is empty). Nothing was sent.");
        }
        if (resolve) {
            OpenEvent ev = open.remove(TEST_FINGERPRINT);
            if (ev == null) {
                return new SendResult(false, "There is no open test event to resolve.");
            }
            return post(payload(ev, "resolved", "info", null, null, null, Instant.now()));
        }
        if (open.containsKey(TEST_FINGERPRINT)) {
            return new SendResult(false, "A test event is already open. Resolve it first.");
        }
        Map<String, String> labels = new LinkedHashMap<>(baseLabels());
        labels.put("kind", "test");
        OpenEvent ev = new OpenEvent("gp-test-" + UUID.randomUUID().toString().substring(0, 8), TEST_FINGERPRINT, "gharpahucho_test_event",
                "GharPahucho test event (safe to ignore)", labels, Instant.now(), Instant.now(), false);
        SendResult r = post(payload(ev, "firing", "info",
                "Sent from the GharPahucho Ops panel to verify the NudgeBee Event Webhook connection.", props.getServiceName(), "service", null));
        if (r.sent()) {
            open.put(TEST_FINGERPRINT, ev);
        }
        return r;
    }

    public Collection<OpenEvent> openEvents() {
        return List.copyOf(open.values());
    }

    public boolean isOpen(String fingerprint) {
        return open.containsKey(fingerprint);
    }

    public NudgeBeeStatus status() {
        return new NudgeBeeStatus(props.isWebhookConfigured(), props.isGraphqlConfigured(), sent.get(), failed.get(), lastEventAt.get(),
                lastResult.get(), open.keySet().stream().sorted().toList(), "https://docs.nudgebee.com/docs/integrations/Webhooks/event_webhook/");
    }

    // ------------------------------------------------------------------ internals

    private Map<String, String> baseLabels() {
        Map<String, String> l = new LinkedHashMap<>();
        l.put("service", props.getServiceName());
        l.put("namespace", props.getNamespace());
        l.put("environment", props.getEnvironment());
        l.put("app", "gharpahucho");
        return l;
    }

    private ObjectNode payload(OpenEvent ev, String status, String severity, String description, String subjectName, String subjectType, Instant endsAt) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", ev.id());
        n.put("fingerprint", ev.fingerprint());
        n.put("status", status);
        n.put("title", ev.title());
        n.put("rule_name", ev.ruleName());
        n.put("severity", severity);
        if (description != null) {
            n.put("description", description);
        }
        n.put("starts_at", ev.startedAt().toString());
        if (endsAt != null) {
            n.put("ends_at", endsAt.toString());
        }
        ObjectNode subject = n.putObject("subject");
        subject.put("name", subjectName == null ? props.getServiceName() : subjectName);
        subject.put("type", subjectType == null ? "service" : subjectType);
        subject.put("namespace", props.getNamespace());
        n.put("source_tool", "GharPahucho");
        ObjectNode labels = n.putObject("labels");
        ev.labels().forEach(labels::put);
        return n;
    }

    private SendResult post(ObjectNode body) {
        String fingerprint = body.path("fingerprint").asText();
        String status = body.path("status").asText();
        try {
            RestClient.RequestBodySpec req = http.post().uri(URI.create(props.getEventWebhookUrl().trim()))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (props.getWebhookToken() != null && !props.getWebhookToken().isBlank()) {
                req = req.header("Authorization", "Bearer " + props.getWebhookToken().trim());
            }
            req.body(body).retrieve().toBodilessEntity();
            sent.incrementAndGet();
            lastEventAt.set(Instant.now());
            lastResult.set("HTTP 2xx: " + status + " " + fingerprint);
            registry.success(IntegrationStatusRegistry.NUDGEBEE);
            log.info("event=nudgebee_event_sent fingerprint={} status={}", fingerprint, status);
            return new SendResult(true, "NudgeBee accepted the " + status + " event (" + fingerprint + ").");
        } catch (RestClientResponseException e) {
            String detail = "HTTP " + e.getStatusCode().value() + (e.getStatusCode().value() == 404
                    ? " (check the path is /api/webhooks/event)" : e.getStatusCode().value() == 400 ? " (payload or token rejected)" : "");
            return failure(fingerprint, detail, e.getResponseBodyAsString());
        } catch (Exception e) {
            return failure(fingerprint, e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    private SendResult failure(String fingerprint, String detail, String body) {
        failed.incrementAndGet();
        lastEventAt.set(Instant.now());
        lastResult.set("FAILED: " + detail);
        registry.failure(IntegrationStatusRegistry.NUDGEBEE, detail);
        log.warn("event=nudgebee_event_failed fingerprint={} detail=\"{}\" body=\"{}\"", fingerprint, detail,
                body == null ? "" : body.substring(0, Math.min(200, body.length())).replace('\n', ' '));
        return new SendResult(false, "NudgeBee did not accept the event: " + detail);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
