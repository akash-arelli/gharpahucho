package com.gharpahucho.integration.flagsmith;

import com.flagsmith.FlagsmithClient;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.Flags;
import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.integration.IntegrationStatusRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Thin, defensive wrapper around the official Flagsmith Java SDK ({@code com.flagsmith:flagsmith-java-client}).
 *
 * <ul>
 *   <li>Uses the server-side environment key (never exposed to the browser).</li>
 *   <li>Local evaluation (default, needs a {@code ser.} key): the SDK downloads the environment document and polls it,
 *       so segments, identity overrides and percentage rollouts are evaluated in-process and flag changes reach the
 *       app within the refresh interval, with no redeploy.</li>
 *   <li>Remote evaluation (client-side key or {@code FLAGSMITH_LOCAL_EVALUATION=false}): every evaluation calls the API.</li>
 *   <li>Any failure yields {@link Optional#empty()}; callers then use safe defaults. Nothing throws into a request.</li>
 * </ul>
 */
@Slf4j
@Component
public class FlagsmithGateway {

    private static final long RETRY_INIT_AFTER_MS = 15_000;

    private final GharPahuchoProperties.Flagsmith props;
    private final IntegrationStatusRegistry registry;
    private final boolean localEvaluation;

    private volatile FlagsmithClient client;
    private volatile long lastInitAttempt;
    private volatile Instant lastSuccess;
    private volatile Instant lastFailure;
    private volatile String lastError;

    public FlagsmithGateway(GharPahuchoProperties properties, IntegrationStatusRegistry registry) {
        this.props = properties.getFlagsmith();
        this.registry = registry;
        this.localEvaluation = props.isLocalEvaluation() && props.getServerKey().startsWith("ser.");
        registry.register(IntegrationStatusRegistry.FLAGSMITH, props.isConfigured(),
                props.isConfigured() ? mode() : "Set FLAGSMITH_SERVER_KEY to connect. Using safe fallback defaults.");
        if (props.isConfigured()) {
            ensureClient();
        } else {
            log.info("Flagsmith is not configured (FLAGSMITH_SERVER_KEY empty): using safe fallback defaults");
        }
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public String mode() {
        if (!props.isConfigured()) {
            return "NOT_CONFIGURED";
        }
        return localEvaluation ? "LOCAL_EVALUATION" : "REMOTE_EVALUATION";
    }

    public String apiUrl() {
        return props.getApiUrl() == null || props.getApiUrl().isBlank() ? "https://edge.api.flagsmith.com/api/v1/" : props.getApiUrl();
    }

    public int refreshIntervalSeconds() {
        return localEvaluation ? props.getRefreshIntervalSeconds() : 0;
    }

    public Instant lastSuccess() {
        return lastSuccess;
    }

    public Instant lastFailure() {
        return lastFailure;
    }

    public String lastError() {
        return lastError;
    }

    public boolean isHealthy() {
        return props.isConfigured() && lastSuccess != null && (lastFailure == null || lastSuccess.isAfter(lastFailure));
    }

    /** Evaluates flags for an identity (user) or, when {@code identity} is null, for the environment. */
    public Optional<Flags> evaluate(String identity, Map<String, Object> traits) {
        if (!props.isConfigured()) {
            return Optional.empty();
        }
        FlagsmithClient c = ensureClient();
        if (c == null) {
            return Optional.empty();
        }
        try {
            if (localEvaluation && c.getEvaluationContext() == null) {
                throw new IllegalStateException("Flagsmith environment document has not been downloaded yet");
            }
            Flags flags = identity == null ? c.getEnvironmentFlags() : c.getIdentityFlags(identity, traits);
            boolean anyReal = flags.getAllFlags().stream().anyMatch(f -> !isDefault(f));
            if (!anyReal) {
                throw new IllegalStateException("Flagsmith returned no features (check the environment key and that the features exist)");
            }
            lastSuccess = Instant.now();
            registry.success(IntegrationStatusRegistry.FLAGSMITH);
            return Optional.of(flags);
        } catch (Exception e) {
            lastFailure = Instant.now();
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            registry.failure(IntegrationStatusRegistry.FLAGSMITH, lastError);
            log.warn("event=flagsmith_evaluation_failed identity={} error={}", identity == null ? "environment" : "user", lastError);
            return Optional.empty();
        }
    }

    public static boolean isDefault(BaseFlag flag) {
        if (flag instanceof DefaultFlag d) {
            return Boolean.TRUE.equals(d.getIsDefault());
        }
        if (flag instanceof com.flagsmith.models.Flag f) {
            return Boolean.TRUE.equals(f.getIsDefault());
        }
        return false;
    }

    private synchronized FlagsmithClient ensureClient() {
        if (client != null) {
            return client;
        }
        long now = System.currentTimeMillis();
        if (now - lastInitAttempt < RETRY_INIT_AFTER_MS) {
            return null;
        }
        lastInitAttempt = now;
        try {
            FlagsmithConfig.Builder cfg = FlagsmithConfig.newBuilder()
                    .connectTimeout(props.getConnectTimeoutMs())
                    .readTimeout(props.getReadTimeoutMs())
                    .writeTimeout(props.getReadTimeoutMs())
                    .retries(new Retry(1));
            if (props.getApiUrl() != null && !props.getApiUrl().isBlank()) {
                cfg.baseUri(props.getApiUrl());
            }
            if (localEvaluation) {
                cfg.withLocalEvaluation(true).withEnvironmentRefreshIntervalSeconds(props.getRefreshIntervalSeconds());
            }
            client = FlagsmithClient.newBuilder()
                    .setApiKey(props.getServerKey())
                    .setDefaultFlagValueFunction(this::fallbackFlag)
                    .withConfiguration(cfg.build())
                    .build();
            log.info("event=flagsmith_client_initialised mode={} api={}", mode(), apiUrl());
        } catch (Exception e) {
            lastFailure = Instant.now();
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            registry.failure(IntegrationStatusRegistry.FLAGSMITH, lastError);
            log.warn("event=flagsmith_init_failed error={}", lastError);
        }
        return client;
    }

    /** Called by the SDK for features that are unknown/unavailable. Marked as default so the source stays honest. */
    private BaseFlag fallbackFlag(String featureName) {
        DefaultFlag flag = new DefaultFlag();
        flag.setFeatureName(featureName);
        flag.setEnabled(props.getDefaults().getOrDefault(featureName, false));
        flag.setValue(props.getConfigDefaults().get(featureName));
        flag.setIsDefault(true);
        return flag;
    }

    @PreDestroy
    void shutdown() {
        FlagsmithClient c = client;
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
