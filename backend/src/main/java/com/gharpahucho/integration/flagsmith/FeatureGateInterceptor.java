package com.gharpahucho.integration.flagsmith;

import com.gharpahucho.exception.FeatureDisabledException;
import com.gharpahucho.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Rejects calls to {@link RequiresFeature} endpoints with HTTP 403 / FEATURE_DISABLED when the flag is off. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureGateInterceptor implements HandlerInterceptor {

    private final FeatureFlagService flags;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresFeature gate = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresFeature.class);
        if (gate == null) {
            gate = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresFeature.class);
        }
        if (gate == null) {
            return true;
        }
        if (!flags.isEnabled(gate.value(), CurrentUser.idOrNull())) {
            log.info("event=feature_gate_blocked feature={} path={}", gate.value().key(), request.getRequestURI());
            throw new FeatureDisabledException(gate.value().key());
        }
        return true;
    }
}
