package com.github.dimitryivaniuta.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.gateway.TestProperties;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for route policy resolution.
 */
class RateLimitPolicyResolverTest {

    private final RateLimitPolicyResolver resolver = new RateLimitPolicyResolver();

    /**
     * Missing route policy falls back to global defaults.
     */
    @Test
    void fallsBackToGlobalPolicy() {
        ClientIdentity identity = new ClientIdentity("ip:127.0.0.1", null, "127.0.0.1", null);

        ResolvedRateLimitPolicy policy = resolver.resolve("unknown", identity, TestProperties.baseline());

        assertThat(policy.baseLimit()).isEqualTo(100);
        assertThat(policy.errorRateThreshold()).isEqualTo(0.20);
    }

    /**
     * Known route policy overrides global defaults.
     */
    @Test
    void resolvesRouteOverride() {
        ClientIdentity identity = new ClientIdentity("ip:127.0.0.1", null, "127.0.0.1", null);

        ResolvedRateLimitPolicy policy = resolver.resolve("auth-api", identity, TestProperties.baseline());

        assertThat(policy.baseLimit()).isEqualTo(30);
        assertThat(policy.errorRateThreshold()).isEqualTo(0.10);
        assertThat(policy.abuseScoreHardBlock()).isEqualTo(60);
    }
}
