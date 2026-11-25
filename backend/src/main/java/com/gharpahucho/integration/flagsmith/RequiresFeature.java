package com.gharpahucho.integration.flagsmith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that is only available when a Flagsmith feature is enabled for the calling user.
 * Enforced server-side by {@link FeatureGateInterceptor}; the UI hiding a button is a convenience, never the control.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    FeatureKey value();
}
