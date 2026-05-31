package com.github.dimitryivaniuta.gateway.ratelimit;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import com.github.dimitryivaniuta.gateway.domain.Decision;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Stores and updates abuse scores in Redis.
 *
 * <p>Per-subject scores expire naturally. Sorted-set dashboards receive only
 * the score delta, not the cumulative score, to avoid inflated rankings.</p>
 */
@Service
@RequiredArgsConstructor
public class AbuseScoreService {

    private static final String SCORE_PREFIX = "arl:abuse:score:";
    private static final String TOP_IPS = "arl:abuse:top:ip";
    private static final String TOP_TENANTS = "arl:abuse:top:tenant";

    private final ReactiveStringRedisTemplate redis;
    private final AdaptiveRateLimitProperties properties;

    /**
     * Reads the current score for a subject.
     *
     * @param subjectKey subject key
     * @return current score, or zero when absent
     */
    public Mono<Double> score(String subjectKey) {
        return redis.opsForValue().get(SCORE_PREFIX + subjectKey)
                .map(this::parse)
                .defaultIfEmpty(0.0)
                .onErrorReturn(0.0);
    }

    /**
     * Increases score based on rejected requests or suspicious response status.
     *
     * @param identity client identity
     * @param decision gateway decision
     * @param status final HTTP status
     * @return updated score
     */
    public Mono<Double> record(ClientIdentity identity, Decision decision, int status) {
        double delta = delta(decision, status);
        if (delta <= 0) {
            return score(identity.subjectKey());
        }
        String scoreKey = SCORE_PREFIX + identity.subjectKey();
        return redis.opsForValue().increment(scoreKey, delta)
                .flatMap(updated -> redis.expire(scoreKey, properties.rateLimit().abuseScoreTtl())
                        .then(updateTopScores(identity, delta))
                        .thenReturn(updated))
                .onErrorReturn(0.0);
    }

    private Mono<Void> updateTopScores(ClientIdentity identity, double delta) {
        Mono<Double> ipScore = redis.opsForZSet().incrementScore(TOP_IPS, identity.clientIp(), delta);
        Mono<Double> tenantScore = identity.tenantId() == null
                ? Mono.just(delta)
                : redis.opsForZSet().incrementScore(TOP_TENANTS, identity.tenantId(), delta);
        return Mono.whenDelayError(ipScore, tenantScore).then();
    }

    private double delta(Decision decision, int status) {
        if (decision == Decision.BLOCKED) {
            return 20;
        }
        if (decision == Decision.RATE_LIMITED || status == 429) {
            return 10;
        }
        if (status == 401 || status == 403) {
            return 8;
        }
        if (status == 404 || status == 422) {
            return 3;
        }
        if (status >= 500) {
            return 1;
        }
        return 0;
    }

    private double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }
}
