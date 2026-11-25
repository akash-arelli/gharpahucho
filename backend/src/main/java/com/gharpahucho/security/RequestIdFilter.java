package com.gharpahucho.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a request id to every request (MDC + response header) and writes one structured access-log line per API call.
 * The line carries method, path, status and duration: the signals NudgeBee/Prometheus/Loki need for troubleshooting.
 * Query strings and bodies are never logged (they may contain personal data).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && incoming.matches("[A-Za-z0-9\\-]{8,64}") ? incoming : UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("event=http_request method={} path={} status={} durationMs={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus(), ms);
            MDC.remove("requestId");
        }
    }
}
