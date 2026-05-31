package com.github.dimitryivaniuta.gateway.domain;

/**
 * Final gateway decision for a request.
 */
public enum Decision {
    /** Request is proxied normally. */
    ALLOWED,
    /** Request is allowed because of explicit allowlist entry. */
    ALLOWLISTED,
    /** Request is denied because of explicit blocklist entry. */
    BLOCKED,
    /** Request is denied because dynamic rate limit is exhausted. */
    RATE_LIMITED
}
