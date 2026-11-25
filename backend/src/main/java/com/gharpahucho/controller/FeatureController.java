package com.gharpahucho.controller;

import com.gharpahucho.dto.FeatureDtos.*;
import com.gharpahucho.integration.flagsmith.*;
import com.gharpahucho.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the Flagsmith evaluation to the UI. Only evaluated results are returned: the server-side environment key
 * never leaves the backend, and the browser cannot choose which identity is evaluated.
 */
@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureFlagService flags;

    /** Flags and remote config evaluated for the signed-in user (identity + traits, segments, rollouts). */
    @GetMapping
    public FeaturesResponse mine() {
        return build(flags.snapshot(CurrentUser.id()));
    }

    /** Environment-level evaluation without an identity, for pages shown before sign-in. */
    @GetMapping("/public")
    public FeaturesResponse environment() {
        return build(flags.snapshot(null));
    }

    private FeaturesResponse build(FeatureSnapshot s) {
        Map<String, Boolean> simple = new LinkedHashMap<>();
        List<FeatureState> states = new ArrayList<>();
        for (FeatureKey key : FeatureKey.values()) {
            FeatureSnapshot.FlagResult r = s.flags().get(key);
            simple.put(key.key(), r.enabled());
            states.add(new FeatureState(key.key(), r.enabled(), r.source(), key.title(), key.description(), key.effect()));
        }
        Map<String, String> config = new LinkedHashMap<>();
        List<ConfigValue> configStates = new ArrayList<>();
        for (ConfigKey key : ConfigKey.values()) {
            FeatureSnapshot.ConfigResult r = s.config().get(key);
            config.put(key.key(), r.value());
            configStates.add(new ConfigValue(key.key(), r.value(), r.source(), key.title(), key.description()));
        }
        FlagsmithGateway g = flags.gateway();
        FlagsmithStatus status = new FlagsmithStatus(g.isConfigured(), g.mode(), g.apiUrl(), g.isHealthy(), g.lastSuccess(), g.lastFailure(),
                g.lastError(), g.refreshIntervalSeconds());
        return new FeaturesResponse(simple, config, states, configStates, s.traits(), s.identity(), status, s.evaluatedAt());
    }
}
