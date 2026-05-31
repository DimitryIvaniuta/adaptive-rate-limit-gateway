package com.github.dimitryivaniuta.gateway.domain;

/**
 * Human and machine readable reason for a gateway decision.
 */
public enum DecisionReason {
    /** No special handling was needed. */
    NORMAL,
    /** Subject is explicitly allowed. */
    ALLOWLIST,
    /** Subject is explicitly blocked. */
    BLOCKLIST,
    /** Redis fixed-window counter exceeded the effective limit. */
    LIMIT_EXCEEDED,
    /** Abuse score crossed the configured hard-block threshold. */
    ABUSE_SCORE_HARD_BLOCK,
    /** Gateway could not reach a protection dependency and was configured to fail open. */
    PROTECTION_INFRASTRUCTURE_ERROR
}
