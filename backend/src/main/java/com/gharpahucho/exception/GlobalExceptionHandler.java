package com.gharpahucho.exception;

import com.gharpahucho.integration.nudgebee.DependencyHealthMonitor;
import com.gharpahucho.integration.nudgebee.NudgeBeeEventPublisher;
import com.gharpahucho.integration.sarvam.SarvamUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One consistent error envelope for the whole API. Unexpected failures return a generic message plus a request id
 * (never a stack trace or internal detail), are logged with the same id, and are forwarded to NudgeBee as alerts.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final NudgeBeeEventPublisher nudgebee;

    public GlobalExceptionHandler(NudgeBeeEventPublisher nudgebee) {
        this.nudgebee = nudgebee;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest req) {
        return build(e.getStatus(), e.getCode(), e.getMessage(), req, e.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(g -> fields.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid", req, Map.of("fields", fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException e, HttpServletRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(r -> r.getResolvableErrors()
                .forEach(err -> fields.putIfAbsent(r.getMethodParameter().getParameterName(), err.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some parameters are invalid", req, Map.of("fields", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e, HttpServletRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some parameters are invalid", req, Map.of("fields", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest req) {
        String msg = e instanceof MethodArgumentTypeMismatchException m ? "Parameter '" + m.getName() + "' has an invalid value"
                : e instanceof MissingServletRequestParameterException p ? "Missing parameter '" + p.getParameterName() + "'"
                : e instanceof MissingServletRequestPartException p ? "Missing part '" + p.getRequestPartName() + "'"
                : "The request body is missing or malformed";
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", msg, req, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e, HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The upload is too large (limit 5 MB)", req, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method " + e.getMethod() + " is not supported here", req, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type", req, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResource(NoResourceFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint", req, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource", req, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> auth(AuthenticationException e, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in to continue", req, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("event=data_integrity_violation path={} cause={}", req.getRequestURI(), rootMessage(e));
        return build(HttpStatus.CONFLICT, "CONFLICT", "That change conflicts with existing data", req, Map.of());
    }

    @ExceptionHandler(SarvamUnavailableException.class)
    ResponseEntity<ApiError> sarvam(SarvamUnavailableException e, HttpServletRequest req) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", e.isNotConfigured()
                ? "AI features need a Sarvam API key. The deterministic features still work."
                : "The AI service is unavailable right now. Please try again shortly.", req, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        String id = MDC.get("requestId");
        log.error("event=unhandled_exception path={} method={} type={}", req.getRequestURI(), req.getMethod(), e.getClass().getName(), e);
        try {
            nudgebee.fire(DependencyHealthMonitor.HTTP_5XX_PREFIX + e.getClass().getSimpleName(), "http_5xx",
                    "GharPahucho API error: " + e.getClass().getSimpleName() + " on " + req.getMethod() + " " + req.getRequestURI(),
                    "Unhandled exception (request id " + id + "): " + rootMessage(e), "high", "gharpahucho-backend", "service",
                    Map.of("exception", e.getClass().getSimpleName()), true);
        } catch (RuntimeException ignored) {
            // reporting must never make an error worse
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again. Quote request id " + id + " if it persists.", req, Map.of());
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        String m = r.getMessage();
        return r.getClass().getSimpleName() + (m == null ? "" : ": " + (m.length() > 200 ? m.substring(0, 200) : m));
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest req, Map<String, Object> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, req.getRequestURI(), MDC.get("requestId"), details);
        return ResponseEntity.status(status).body(body);
    }
}
