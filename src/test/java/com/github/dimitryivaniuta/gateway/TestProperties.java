package com.github.dimitryivaniuta.gateway;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Test factory for concise configuration construction in unit tests.
 */
public final class TestProperties {

    private TestProperties() {
    }

    /**
     * Returns a valid baseline property set.
     */
    public static AdaptiveRateLimitProperties baseline() {
        return new AdaptiveRateLimitProperties(
                "test-token",
                true,
                List.of("0.0.0.0/0"),
                new AdaptiveRateLimitProperties.RateLimit(
                        true,
                        true,
                        Duration.ofMinutes(1),
                        100,
                        500,
                        10,
                        true,
                        0.20,
                        100,
                        5,
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(10),
                        Duration.ofHours(2),
                        List.of(400, 401, 403, 404, 409, 422, 429),
                        Map.of("auth-api", new AdaptiveRateLimitProperties.RoutePolicy(
                                30, 120, 5, Duration.ofMinutes(1), 0.10, 60
                        ))
                ),
                new AdaptiveRateLimitProperties.Kafka(false, "topic"),
                new AdaptiveRateLimitProperties.Audit(true, 0.0)
        );
    }
}
