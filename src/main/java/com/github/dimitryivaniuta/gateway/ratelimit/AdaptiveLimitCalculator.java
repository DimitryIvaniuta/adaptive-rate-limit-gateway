package com.github.dimitryivaniuta.gateway.ratelimit;

import org.springframework.stereotype.Component;

/**
 * Calculates a dynamic per-window limit from a base limit, recent error rate, and abuse score.
 */
@Component
public class AdaptiveLimitCalculator {

    /**
     * Calculates the effective limit for the current identity and route policy.
     *
     * <p>The formula is intentionally cheap and deterministic. It avoids ML
     * scoring on the hot path while still reacting to bad recent behavior:</p>
     *
     * <pre>
     * effective = clamp(base * (1 - errorPenalty - abusePenalty), minimum, base)
     * </pre>
     *
     * @param policy resolved route policy
     * @param errorRate recent error ratio from 0.0 to 1.0
     * @param abuseScore Redis abuse score from 0 upward
     * @return effective request limit for the configured window
     */
    public int calculate(ResolvedRateLimitPolicy policy, double errorRate, double abuseScore) {
        double errorPenalty = errorPenalty(errorRate, policy.errorRateThreshold());
        double abusePenalty = Math.min(0.80, Math.max(0, abuseScore) / 125.0);
        double multiplier = Math.max(0.0, 1.0 - errorPenalty - abusePenalty);
        int calculated = (int) Math.floor(policy.baseLimit() * multiplier);
        return Math.max(policy.minimumLimit(), Math.min(policy.baseLimit(), calculated));
    }

    private double errorPenalty(double errorRate, double threshold) {
        if (errorRate <= threshold) {
            return 0.0;
        }
        return Math.min(0.50, (errorRate - threshold) * 1.25);
    }
}
