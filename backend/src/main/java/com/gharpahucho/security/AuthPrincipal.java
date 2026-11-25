package com.gharpahucho.security;

import java.util.UUID;

/** The authenticated caller, derived from a verified JWT. */
public record AuthPrincipal(UUID userId, String email) {
}
