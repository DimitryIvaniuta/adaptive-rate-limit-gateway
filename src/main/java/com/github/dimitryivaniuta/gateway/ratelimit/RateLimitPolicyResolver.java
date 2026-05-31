package com.github.dimitryivaniuta.gateway.ratelimit;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the effective policy by combining global defaults with route-specific overrides.
 */
@Component
public class RateLimitPolicyResolver {

    /**
     * Resolves a policy for the current route and identity dimension.
     *
     * @param routeId Gateway route id
     * @param identity resolved client identity
     * @param properties externalized gateway configuration
     * @return resolved policy ready for fast calculation
     */
    public ResolvedRateLimitPolicy resolve(String routeId,
                                           ClientIdentity identity,
                                           AdaptiveRateLimitProperties properties) {
        AdaptiveRateLimitProperties.RateLimit defaults = properties.rateLimit();
        AdaptiveRateLimitProperties.RoutePolicy override = routePolicy(routeId, defaults.routePolicies());
        boolean tenantScoped = StringUtils.hasText(identity.tenantId());
        int base = tenantScoped
                ? integerOrDefault(override == null ? null : override.tenantBaseLimitPerMinute(), defaults.tenantBaseLimitPerMinute())
                : integerOrDefault(override == null ? null : override.baseLimitPerMinute(), defaults.baseLimitPerMinute());
        int minimum = integerOrDefault(override == null ? null : override.minimumLimitPerMinute(), defaults.minimumLimitPerMinute());
        return new ResolvedRateLimitPolicy(
                StringUtils.hasText(routeId) ? routeId : "unknown-route",
                durationOrDefault(override == null ? null : override.window(), defaults.window()),
                Math.max(1, base),
                Math.max(1, Math.min(minimum, base)),
                doubleOrDefault(override == null ? null : override.errorRateThreshold(), defaults.errorRateThreshold()),
                integerOrDefault(override == null ? null : override.abuseScoreHardBlock(), defaults.abuseScoreHardBlock())
        );
    }

    private AdaptiveRateLimitProperties.RoutePolicy routePolicy(String routeId,
                                                               Map<String, AdaptiveRateLimitProperties.RoutePolicy> policies) {
        if (!StringUtils.hasText(routeId) || policies == null || policies.isEmpty()) {
            return null;
        }
        return policies.get(routeId);
    }

    private int integerOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private double doubleOrDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private Duration durationOrDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
