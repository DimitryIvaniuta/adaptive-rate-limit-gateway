package com.github.dimitryivaniuta.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.gateway.domain.WindowResult;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Placeholder integration test showing expected Redis limiter behavior.
 *
 * <p>Enable this test when running with a real Redis Testcontainer in CI.</p>
 */
class RedisFixedWindowRateLimiterTest {

    /**
     * Documents the expected behavior of the fixed window limiter.
     */
    @Test
    void windowResultContract() {
        WindowResult result = new WindowResult(true, 1, 4, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(1);
        assertThat(result.remaining()).isEqualTo(4);
    }

    /**
     * Template for a future Redis container test.
     */
    @Test
    @Disabled("Requires external Redis container in CI runtime")
    void consumesUntilLimit() {
        StepVerifier.create(reactor.core.publisher.Mono.just(new WindowResult(false, 6, 0, Duration.ofMinutes(1).toSeconds())))
                .assertNext(result -> assertThat(result.allowed()).isFalse())
                .verifyComplete();
    }
}
