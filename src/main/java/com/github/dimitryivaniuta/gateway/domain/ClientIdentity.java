package com.github.dimitryivaniuta.gateway.domain;

/**
 * Resolved client identity used for access lists, rate-limit keys, and audit rows.
 *
 * @param subjectKey stable rate-limit key, preferring tenant over API key over IP
 * @param tenantId optional tenant identifier
 * @param clientIp resolved client IP address
 * @param apiKeyHash optional SHA-256 API key hash
 */
public record ClientIdentity(
        String subjectKey,
        String tenantId,
        String clientIp,
        String apiKeyHash
) {
}
