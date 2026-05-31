package com.github.dimitryivaniuta.gateway.audit;

import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import com.github.dimitryivaniuta.gateway.domain.Decision;
import com.github.dimitryivaniuta.gateway.domain.DecisionReason;
import java.time.OffsetDateTime;

/**
 * Immutable record inserted into the rate-limit audit table.
 */
public record RateLimitAuditRecord(
        OffsetDateTime occurredAt,
        String requestId,
        String routeId,
        ClientIdentity identity,
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
