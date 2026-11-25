package com.gharpahucho.integration.sarvam;

/**
 * Raised when Sarvam AI cannot be used: no API key configured, a network failure, a quota/auth error or an unusable
 * response. Callers translate this into an honest fallback; they never pretend the call succeeded.
 */
public class SarvamUnavailableException extends RuntimeException {

    private final boolean notConfigured;

    public SarvamUnavailableException(String message, boolean notConfigured) {
        super(message);
        this.notConfigured = notConfigured;
    }

    public SarvamUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.notConfigured = false;
    }

    public boolean isNotConfigured() {
        return notConfigured;
    }
}
