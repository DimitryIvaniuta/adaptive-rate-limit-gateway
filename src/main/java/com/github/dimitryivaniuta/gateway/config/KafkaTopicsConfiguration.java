package com.github.dimitryivaniuta.gateway.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for abuse-event publishing.
 */
@Configuration
@RequiredArgsConstructor
public class KafkaTopicsConfiguration {

    private final AdaptiveRateLimitProperties properties;

    /**
     * Creates the abuse events topic in local/dev environments when auto topic creation is enabled.
     *
     * <p>Abuse events are an append-only analytical stream, so the topic uses
     * retention rather than compaction. Downstream consumers can build long-term
     * offender history from this stream.</p>
     *
     * @return Kafka topic definition
     */
    @Bean
    @ConditionalOnProperty(prefix = "gateway.kafka", name = "enabled", havingValue = "true")
    NewTopic abuseEventsTopic() {
        return TopicBuilder.name(properties.kafka().abuseTopic())
                .partitions(6)
                .replicas(1)
                .config("retention.ms", Long.toString(7L * 24 * 60 * 60 * 1000))
                .config("cleanup.policy", "delete")
                .build();
    }
}
