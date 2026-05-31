package com.github.dimitryivaniuta.gateway.domain;

import java.time.Instant;

/**
 * Kafka event emitted for blocked, rate-limited, or suspicious traffic.
 */
public record AbuseTrafficEvent(
        Instant occurredAt,
        String requestId,
        String routeId,
        String subjectKey,
        String tenantId,
        String clientIp,
        String method,
        String path,
        Integer statusCode,
        Decision decision,
        DecisionReason reason,
        int effectiveLimit,
        int remainingTokens,
        double abuseScore,
        double errorRate
) {
}
