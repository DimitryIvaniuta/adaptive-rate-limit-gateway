package com.github.dimitryivaniuta.gateway.messaging;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.AbuseTrafficEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka-backed abuse-event publisher.
 */
@Component
@ConditionalOnProperty(prefix = "gateway.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaAbuseEventPublisher implements AbuseEventPublisher {

    private final KafkaTemplate<String, AbuseTrafficEvent> kafkaTemplate;
    private final AdaptiveRateLimitProperties properties;

    /**
     * Sends the event keyed by subject for ordered downstream processing per offender.
     */
    @Override
    public Mono<Void> publish(AbuseTrafficEvent event) {
        return Mono.fromFuture(kafkaTemplate.send(properties.kafka().abuseTopic(), event.subjectKey(), event))
                .then()
                .onErrorResume(ex -> Mono.empty());
    }
}
