package com.github.dimitryivaniuta.gateway.ratelimit;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Maintains low-cost Redis counters for recent response telemetry.
 *
 * <p>Error rate is calculated across several minute buckets instead of only
 * the current minute. This avoids unstable decisions at minute boundaries.</p>
 */
@Service
@RequiredArgsConstructor
public class TrafficStatsService {

    private static final String TOTAL_PREFIX = "arl:stats:total:";
    private static final String ERROR_PREFIX = "arl:stats:error:";

    private final ReactiveStringRedisTemplate redis;
    private final AdaptiveRateLimitProperties properties;
    private final Clock clock = Clock.systemUTC();

    /**
     * Gets the recent error ratio for a subject from a small rolling bucket set.
     *
     * @param subjectKey subject key
     * @return ratio between 0.0 and 1.0
     */
    public Mono<Double> errorRate(String subjectKey) {
        return redis.opsForValue().multiGet(statKeys(subjectKey))
                .map(values -> {
                    long total = 0;
                    long errors = 0;
                    for (int i = 0; i < values.size(); i += 2) {
                        total += parse(values.get(i));
                        errors += parse(values.get(i + 1));
                    }
                    if (total <= 0) {
                        return 0.0;
                    }
                    return Math.min(1.0, (double) errors / (double) total);
                })
                .defaultIfEmpty(0.0)
                .onErrorReturn(0.0);
    }

    /**
     * Records final response status for future adaptive decisions.
     *
     * @param subjectKey subject key
     * @param status HTTP response status
     * @return completion signal
     */
    public Mono<Void> record(String subjectKey, int status) {
        String suffix = bucketSuffix(subjectKey, currentMinute());
        Duration ttl = properties.rateLimit().statisticsTtl();
        Mono<Long> total = redis.opsForValue().increment(TOTAL_PREFIX + suffix)
                .flatMap(count -> expireOnFirst(TOTAL_PREFIX + suffix, count, ttl).thenReturn(count));
        if (!properties.rateLimit().responseErrorStatuses().contains(status)) {
            return total.then().onErrorResume(ex -> Mono.empty());
        }
        Mono<Long> errors = redis.opsForValue().increment(ERROR_PREFIX + suffix)
                .flatMap(count -> expireOnFirst(ERROR_PREFIX + suffix, count, ttl).thenReturn(count));
        return total.then(errors).then().onErrorResume(ex -> Mono.empty());
    }

    private List<String> statKeys(String subjectKey) {
        int bucketCount = Math.max(1, properties.rateLimit().statisticsBuckets());
        long minute = currentMinute();
        List<String> keys = new ArrayList<>(bucketCount * 2);
        for (int i = 0; i < bucketCount; i++) {
            String suffix = bucketSuffix(subjectKey, minute - i);
            keys.add(TOTAL_PREFIX + suffix);
            keys.add(ERROR_PREFIX + suffix);
        }
        return keys;
    }

    private String bucketSuffix(String subjectKey, long minute) {
        return subjectKey + ':' + minute;
    }

    private long currentMinute() {
        return clock.instant().getEpochSecond() / 60;
    }

    private Mono<Boolean> expireOnFirst(String key, Long count, Duration ttl) {
        if (count != null && count == 1L) {
            return redis.expire(key, ttl);
        }
        return Mono.just(Boolean.TRUE);
    }

    private long parse(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
