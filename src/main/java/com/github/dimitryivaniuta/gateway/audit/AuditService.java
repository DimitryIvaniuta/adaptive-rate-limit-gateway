package com.github.dimitryivaniuta.gateway.audit;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.Decision;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Decides whether to persist audit data and shields the request path from audit failures.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final RateLimitAuditRepository repository;
    private final AdaptiveRateLimitProperties properties;

    /**
     * Persists an audit record when it is important enough or selected by sampling.
     *
     * @param record audit record
     * @return completion signal that never fails the request path
     */
    public Mono<Void> persist(RateLimitAuditRecord record) {
        if (!properties.audit().enabled() || !shouldPersist(record)) {
            return Mono.empty();
        }
        return repository.insert(record).onErrorResume(ex -> Mono.empty());
    }

    private boolean shouldPersist(RateLimitAuditRecord record) {
        if (record.decision() == Decision.BLOCKED || record.decision() == Decision.RATE_LIMITED) {
            return true;
        }
        if (record.statusCode() != null && record.statusCode() >= 400) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < properties.audit().persistAllowedSampleRate();
    }
}
