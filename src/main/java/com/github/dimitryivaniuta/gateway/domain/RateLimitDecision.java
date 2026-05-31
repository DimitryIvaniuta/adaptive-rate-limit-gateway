package com.github.dimitryivaniuta.gateway.domain;

/**
 * Full rate-limit decision with calculated dynamic values.
 *
 * @param decision final decision
 * @param reason reason for the decision
 * @param effectiveLimit calculated per-window limit
 * @param remainingTokens estimated remaining tokens for the current window
 * @param resetSeconds seconds until the Redis window resets
 * @param abuseScore current abuse score
 * @param errorRate recent error rate
 */
public record RateLimitDecision(
        Decision decision,
        DecisionReason reason,
        int effectiveLimit,
        int remainingTokens,
        long resetSeconds,
        double abuseScore,
        double errorRate
) {
    /**
     * @return true when the request may continue to the upstream route
     */
    public boolean allowed() {
        return decision == Decision.ALLOWED || decision == Decision.ALLOWLISTED;
    }
}
