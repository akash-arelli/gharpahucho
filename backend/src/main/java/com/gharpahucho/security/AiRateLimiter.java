package com.gharpahucho.security;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-user sliding-window limiter for endpoints that spend Sarvam credits (assistant, voice, parse).
 * In-memory by design: good enough for a single instance; swap for a shared limiter when scaling out.
 */
@Component
public class AiRateLimiter {

    private final int limitPerMinute;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    public AiRateLimiter(GharPahuchoProperties props) {
        this.limitPerMinute = Math.max(1, props.getSarvam().getRateLimitPerMinute());
    }

    public void check(UUID userId) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(userId, (k, old) -> {
            if (old == null || now - old.start >= 60_000) {
                return new Window(now, 1);
            }
            return new Window(old.start, old.count + 1);
        });
        if (w.count > limitPerMinute) {
            long retry = Math.max(1, (60_000 - (now - w.start)) / 1000);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "You are sending requests too quickly. Please retry in " + retry + "s.", Map.of("retryAfterSeconds", retry));
        }
    }

    private record Window(long start, int count) {
    }
}
