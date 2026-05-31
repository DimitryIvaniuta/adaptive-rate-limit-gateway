package com.github.dimitryivaniuta.gateway.messaging;

import com.github.dimitryivaniuta.gateway.domain.AbuseTrafficEvent;
import reactor.core.publisher.Mono;

/**
 * Publishes abuse events to an external stream.
 */
public interface AbuseEventPublisher {

    /**
     * Publishes an event asynchronously.
     *
     * @param event abuse event
     * @return completion signal
     */
    Mono<Void> publish(AbuseTrafficEvent event);
}
