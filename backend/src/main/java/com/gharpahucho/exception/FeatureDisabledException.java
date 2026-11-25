package com.gharpahucho.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Thrown when a request needs a Flagsmith feature that is currently switched off for this user. */
public class FeatureDisabledException extends ApiException {

    public FeatureDisabledException(String featureKey) {
        super(HttpStatus.FORBIDDEN, "FEATURE_DISABLED",
                "This capability is currently switched off (feature flag '" + featureKey + "').",
                Map.of("feature", featureKey));
    }
}
