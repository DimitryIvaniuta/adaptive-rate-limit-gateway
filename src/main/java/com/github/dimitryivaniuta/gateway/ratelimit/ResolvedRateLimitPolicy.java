package com.github.dimitryivaniuta.gateway.ratelimit;

import java.time.Duration;

/**
 * Fully resolved rate-limit policy for a single request and route.
 *
 * @param routeId Gateway route id, or {@code unknown-route} when route metadata is absent
 * @param window Redis rate-limit window
 * @param baseLimit request allowance before adaptive penalties are applied
 * @param minimumLimit lower bound for degraded-but-not-blocked clients
 * @param errorRateThreshold tolerated recent error ratio before throttling starts
 * @param abuseScoreHardBlock score at which the subject is blocked without proxying
 */
public record ResolvedRateLimitPolicy(
        String routeId,
        Duration window,
        int baseLimit,
        int minimumLimit,
        double errorRateThreshold,
        int abuseScoreHardBlock
) {
}
