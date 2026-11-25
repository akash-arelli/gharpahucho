package com.gharpahucho.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.gharpahucho.exception.ApiException;

import java.util.Optional;
import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Optional<AuthPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static UUID id() {
        return principal().map(AuthPrincipal::userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in to continue"));
    }

    public static UUID idOrNull() {
        return principal().map(AuthPrincipal::userId).orElse(null);
    }
}
