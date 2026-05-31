package com.github.dimitryivaniuta.gateway.domain;

/**
 * Aggregated abuse dashboard row.
 *
 * @param subject IP, tenant, or another grouped value
 * @param rejected number of blocked or rate-limited requests
 * @param errors number of error responses recorded by the gateway
 * @param maxAbuseScore maximum score seen in the requested window
 * @param maxErrorRate maximum error rate seen in the requested window
 */
public record DashboardRow(
        String subject,
        long rejected,
        long errors,
        double maxAbuseScore,
        double maxErrorRate
) {
}
