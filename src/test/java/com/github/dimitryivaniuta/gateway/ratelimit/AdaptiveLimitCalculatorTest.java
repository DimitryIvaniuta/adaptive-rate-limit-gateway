package com.github.dimitryivaniuta.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.gateway.TestProperties;
import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for adaptive limit calculation.
 */
class AdaptiveLimitCalculatorTest {

    private final AdaptiveLimitCalculator calculator = new AdaptiveLimitCalculator();
    private final AdaptiveRateLimitProperties properties = TestProperties.baseline();
    private final RateLimitPolicyResolver policyResolver = new RateLimitPolicyResolver();

    /**
     * Healthy IP traffic gets the base IP limit.
     */
    @Test
    void returnsBaseIpLimitForHealthyTraffic() {
        ClientIdentity identity = new ClientIdentity("ip:127.0.0.1", null, "127.0.0.1", null);
        ResolvedRateLimitPolicy policy = policyResolver.resolve("protected-api", identity, properties);

        int result = calculator.calculate(policy, 0.01, 0);

        assertThat(result).isEqualTo(100);
    }

    /**
     * Tenant traffic gets tenant-specific base limit.
     */
    @Test
    void returnsTenantBaseLimitForHealthyTenant() {
        ClientIdentity identity = new ClientIdentity("tenant:acme", "acme", "127.0.0.1", null);
        ResolvedRateLimitPolicy policy = policyResolver.resolve("protected-api", identity, properties);

        int result = calculator.calculate(policy, 0.01, 0);

        assertThat(result).isEqualTo(500);
    }

    /**
     * Route-specific policy overrides global defaults.
     */
    @Test
    void appliesRouteSpecificPolicy() {
        ClientIdentity identity = new ClientIdentity("ip:127.0.0.1", null, "127.0.0.1", null);
        ResolvedRateLimitPolicy policy = policyResolver.resolve("auth-api", identity, properties);

        int result = calculator.calculate(policy, 0.01, 0);

        assertThat(result).isEqualTo(30);
        assertThat(policy.minimumLimit()).isEqualTo(5);
    }

    /**
     * Error rate and abuse score reduce but never go below configured minimum.
     */
    @Test
    void clampsDegradedTrafficToMinimumLimit() {
        ClientIdentity identity = new ClientIdentity("ip:127.0.0.1", null, "127.0.0.1", null);
        ResolvedRateLimitPolicy policy = policyResolver.resolve("protected-api", identity, properties);

        int result = calculator.calculate(policy, 1.0, 500);

        assertThat(result).isEqualTo(10);
    }

    /**
     * Moderate abuse gets a partially reduced limit.
     */
    @Test
    void reducesLimitForModerateAbuse() {
        ClientIdentity identity = new ClientIdentity("ip:127.0.0.1", null, "127.0.0.1", null);
        ResolvedRateLimitPolicy policy = policyResolver.resolve("protected-api", identity, properties);

        int result = calculator.calculate(policy, 0.25, 25);

        assertThat(result).isBetween(50, 90);
    }
}
