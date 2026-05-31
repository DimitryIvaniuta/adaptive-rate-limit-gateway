package com.github.dimitryivaniuta.gateway.audit;

import com.github.dimitryivaniuta.gateway.domain.DashboardRow;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Custom R2DBC repository for audit insertions and dashboard aggregations.
 */
@Repository
@RequiredArgsConstructor
public class RateLimitAuditRepository {

    private final DatabaseClient databaseClient;

    /**
     * Inserts an audit record for suspicious, rejected, or sampled allowed traffic.
     *
     * @param record audit record
     * @return completion signal
     */
    public Mono<Void> insert(RateLimitAuditRecord record) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO rate_limit_audit(
                            occurred_at, request_id, route_id, subject_key, tenant_id, client_ip, api_key_hash,
                            method, path, status_code, decision, reason, effective_limit, remaining_tokens,
                            abuse_score, error_rate
                        ) VALUES (
                            :occurredAt, :requestId, :routeId, :subjectKey, :tenantId, :clientIp, :apiKeyHash,
                            :method, :path, :statusCode, :decision, :reason, :effectiveLimit, :remainingTokens,
                            :abuseScore, :errorRate
                        )
                        """)
                .bind("occurredAt", record.occurredAt())
                .bind("subjectKey", record.identity().subjectKey())
                .bind("method", record.method())
                .bind("path", record.path())
                .bind("decision", record.decision().name())
                .bind("reason", record.reason().name())
                .bind("effectiveLimit", record.effectiveLimit())
                .bind("remainingTokens", record.remainingTokens())
                .bind("abuseScore", record.abuseScore())
                .bind("errorRate", record.errorRate());
        spec = bindNullable(spec, "requestId", record.requestId(), String.class);
        spec = bindNullable(spec, "routeId", record.routeId(), String.class);
        spec = bindNullable(spec, "tenantId", record.identity().tenantId(), String.class);
        spec = bindNullable(spec, "clientIp", record.identity().clientIp(), String.class);
        spec = bindNullable(spec, "apiKeyHash", record.identity().apiKeyHash(), String.class);
        spec = bindNullable(spec, "statusCode", record.statusCode(), Integer.class);
        return spec.fetch().rowsUpdated().then();
    }

    /**
     * Aggregates top abusive IP addresses for an interval.
     */
    public Flux<DashboardRow> topIps(OffsetDateTime from, int limit) {
        return dashboard("client_ip", from, limit);
    }

    /**
     * Aggregates top abusive tenant ids for an interval.
     */
    public Flux<DashboardRow> topTenants(OffsetDateTime from, int limit) {
        return dashboard("tenant_id", from, limit);
    }

    /**
     * Aggregates top routes impacted by abusive or erroneous traffic.
     */
    public Flux<DashboardRow> topRoutes(OffsetDateTime from, int limit) {
        return dashboard("route_id", from, limit);
    }

    private Flux<DashboardRow> dashboard(String column, OffsetDateTime from, int limit) {
        String sql = """
                SELECT %s AS subject,
                       COUNT(*) FILTER (WHERE decision IN ('BLOCKED', 'RATE_LIMITED')) AS rejected,
                       COUNT(*) FILTER (WHERE status_code >= 400) AS errors,
                       COALESCE(MAX(abuse_score), 0) AS max_abuse_score,
                       COALESCE(MAX(error_rate), 0) AS max_error_rate
                  FROM rate_limit_audit
                 WHERE occurred_at >= :from
                   AND %s IS NOT NULL
                 GROUP BY %s
                 ORDER BY rejected DESC, errors DESC, max_abuse_score DESC
                 LIMIT :limit
                """.formatted(column, column, column);
        return databaseClient.sql(sql)
                .bind("from", from)
                .bind("limit", limit)
                .map((row, metadata) -> new DashboardRow(
                        row.get("subject", String.class),
                        value(row.get("rejected", Number.class)),
                        value(row.get("errors", Number.class)),
                        doubleValue(row.get("max_abuse_score", Number.class)),
                        doubleValue(row.get("max_error_rate", Number.class))
                ))
                .all();
    }

    private <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private long value(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private double doubleValue(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
