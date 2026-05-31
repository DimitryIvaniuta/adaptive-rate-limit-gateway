package com.github.dimitryivaniuta.gateway.messaging;

import com.github.dimitryivaniuta.gateway.domain.AbuseTrafficEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * No-op publisher used when Kafka integration is disabled.
 */
@Component
@ConditionalOnMissingBean(AbuseEventPublisher.class)
public class NoopAbuseEventPublisher implements AbuseEventPublisher {

    /**
     * Ignores the event and completes immediately.
     */
    @Override
    public Mono<Void> publish(AbuseTrafficEvent event) {
        return Mono.empty();
    }
}
