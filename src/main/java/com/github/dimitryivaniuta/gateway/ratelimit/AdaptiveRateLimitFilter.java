package com.github.dimitryivaniuta.gateway.ratelimit;

import com.github.dimitryivaniuta.gateway.access.AccessListService;
import com.github.dimitryivaniuta.gateway.audit.AuditService;
import com.github.dimitryivaniuta.gateway.audit.RateLimitAuditRecord;
import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.AbuseTrafficEvent;
import com.github.dimitryivaniuta.gateway.domain.AccessListMode;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import com.github.dimitryivaniuta.gateway.domain.Decision;
import com.github.dimitryivaniuta.gateway.domain.DecisionReason;
import com.github.dimitryivaniuta.gateway.domain.RateLimitDecision;
import com.github.dimitryivaniuta.gateway.domain.WindowResult;
import com.github.dimitryivaniuta.gateway.messaging.AbuseEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global gateway filter that enforces blocklists, allowlists, and adaptive rate limits.
 */
@Component
@RequiredArgsConstructor
public class AdaptiveRateLimitFilter implements GlobalFilter, Ordered {

    private static final String DECISION_ATTRIBUTE = "adaptiveRateLimitDecision";
    private static final String IDENTITY_ATTRIBUTE = "adaptiveClientIdentity";

    private final ClientIdentityResolver identityResolver;
    private final AccessListService accessListService;
    private final AbuseScoreService abuseScoreService;
    private final TrafficStatsService trafficStatsService;
    private final RateLimitPolicyResolver policyResolver;
    private final AdaptiveLimitCalculator limitCalculator;
    private final RedisFixedWindowRateLimiter redisRateLimiter;
    private final AuditService auditService;
    private final AbuseEventPublisher eventPublisher;
    private final AdaptiveRateLimitProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * Runs before the route is proxied to protect upstream services.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.rateLimit().enabled() || isAdminOrActuator(exchange)) {
            return chain.filter(exchange);
        }
        ClientIdentity identity = identityResolver.resolve(exchange);
        String routeId = routeId(exchange);
        exchange.getAttributes().put(IDENTITY_ATTRIBUTE, identity);
        return decide(identity, routeId)
                .onErrorResume(ex -> protectionFailureDecision(identity, routeId))
                .flatMap(decision -> handleDecision(exchange, chain, identity, decision));
    }

    /**
     * Places the filter early in the gateway chain but after admin security.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private Mono<Void> handleDecision(ServerWebExchange exchange,
                                      GatewayFilterChain chain,
                                      ClientIdentity identity,
                                      RateLimitDecision decision) {
        exchange.getAttributes().put(DECISION_ATTRIBUTE, decision);
        writeHeaders(exchange.getResponse(), decision);
        if (!decision.allowed()) {
            exchange.getResponse().setStatusCode(decision.decision() == Decision.BLOCKED
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.TOO_MANY_REQUESTS);
            return finalizeRejected(exchange, identity, decision);
        }
        return chain.filter(exchange)
                .then(Mono.defer(() -> finalizeAllowed(exchange, identity, decision)))
                .onErrorResume(ex -> finalizeErrored(exchange, identity, decision).then(Mono.error(ex)));
    }

    private Mono<RateLimitDecision> decide(ClientIdentity identity, String routeId) {
        return accessListService.resolve(identity)
                .flatMap(mode -> {
                    if (mode.isPresent() && mode.get() == AccessListMode.BLOCK) {
                        return Mono.just(new RateLimitDecision(Decision.BLOCKED, DecisionReason.BLOCKLIST, 0, 0, 0, 0, 0));
                    }
                    ResolvedRateLimitPolicy policy = policyResolver.resolve(routeId, identity, properties);
                    if (mode.isPresent() && mode.get() == AccessListMode.ALLOW && properties.rateLimit().allowlistBypass()) {
                        return Mono.just(new RateLimitDecision(Decision.ALLOWLISTED, DecisionReason.ALLOWLIST,
                                policy.baseLimit(), policy.baseLimit(), policy.window().toSeconds(), 0, 0));
                    }
                    return abuseScoreService.score(identity.subjectKey())
                            .zipWith(trafficStatsService.errorRate(identity.subjectKey()))
                            .flatMap(tuple -> dynamicDecision(identity, policy, tuple.getT1(), tuple.getT2()));
                });
    }

    private Mono<RateLimitDecision> dynamicDecision(ClientIdentity identity,
                                                    ResolvedRateLimitPolicy policy,
                                                    double abuseScore,
                                                    double errorRate) {
        if (abuseScore >= policy.abuseScoreHardBlock()) {
            return Mono.just(new RateLimitDecision(
                    Decision.BLOCKED,
                    DecisionReason.ABUSE_SCORE_HARD_BLOCK,
                    0,
                    0,
                    policy.window().toSeconds(),
                    abuseScore,
                    errorRate
            ));
        }
        int effectiveLimit = limitCalculator.calculate(policy, errorRate, abuseScore);
        return redisRateLimiter.tryConsume(identity.subjectKey(), effectiveLimit, policy.window())
                .map(window -> fromWindow(window, effectiveLimit, abuseScore, errorRate));
    }

    private Mono<RateLimitDecision> protectionFailureDecision(ClientIdentity identity, String routeId) {
        if (!properties.rateLimit().failOpen()) {
            return Mono.just(new RateLimitDecision(Decision.RATE_LIMITED,
                    DecisionReason.PROTECTION_INFRASTRUCTURE_ERROR, 0, 0, 1, 0, 0));
        }
        ResolvedRateLimitPolicy policy = policyResolver.resolve(routeId, identity, properties);
        return Mono.just(new RateLimitDecision(Decision.ALLOWED,
                DecisionReason.PROTECTION_INFRASTRUCTURE_ERROR,
                policy.baseLimit(), policy.baseLimit(), policy.window().toSeconds(), 0, 0));
    }

    private RateLimitDecision fromWindow(WindowResult window, int effectiveLimit, double abuseScore, double errorRate) {
        if (window.allowed()) {
            return new RateLimitDecision(Decision.ALLOWED, DecisionReason.NORMAL, effectiveLimit, window.remaining(), window.resetSeconds(), abuseScore, errorRate);
        }
        return new RateLimitDecision(Decision.RATE_LIMITED, DecisionReason.LIMIT_EXCEEDED, effectiveLimit, 0, window.resetSeconds(), abuseScore, errorRate);
    }

    private Mono<Void> finalizeRejected(ServerWebExchange exchange, ClientIdentity identity, RateLimitDecision decision) {
        int status = exchange.getResponse().getStatusCode() == null ? 429 : exchange.getResponse().getStatusCode().value();
        return finalizeRequest(exchange, identity, decision, status)
                .then(writeProblemBody(exchange, status, decision));
    }

    private Mono<Void> finalizeAllowed(ServerWebExchange exchange, ClientIdentity identity, RateLimitDecision decision) {
        int status = exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value();
        return finalizeRequest(exchange, identity, decision, status);
    }

    private Mono<Void> finalizeErrored(ServerWebExchange exchange, ClientIdentity identity, RateLimitDecision decision) {
        return finalizeRequest(exchange, identity, decision, 500);
    }

    private Mono<Void> finalizeRequest(ServerWebExchange exchange, ClientIdentity identity, RateLimitDecision decision, int status) {
        incrementMetrics(decision, status);
        RateLimitAuditRecord record = auditRecord(exchange, identity, decision, status);
        AbuseTrafficEvent event = event(exchange, identity, decision, status);
        Mono<Double> scoreUpdate = abuseScoreService.record(identity, decision.decision(), status);
        Mono<Void> stats = trafficStatsService.record(identity.subjectKey(), status);
        Mono<Void> audit = auditService.persist(record);
        Mono<Void> publish = shouldPublish(decision, status) ? eventPublisher.publish(event) : Mono.empty();
        return Mono.whenDelayError(scoreUpdate.then(), stats, audit, publish).then();
    }

    private boolean shouldPublish(RateLimitDecision decision, int status) {
        return decision.decision() == Decision.BLOCKED
                || decision.decision() == Decision.RATE_LIMITED
                || decision.reason() == DecisionReason.PROTECTION_INFRASTRUCTURE_ERROR
                || status == 401
                || status == 403
                || status == 429;
    }

    private RateLimitAuditRecord auditRecord(ServerWebExchange exchange, ClientIdentity identity, RateLimitDecision decision, int status) {
        return new RateLimitAuditRecord(
                OffsetDateTime.now(ZoneOffset.UTC),
                exchange.getRequest().getId(),
                routeId(exchange),
                identity,
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getURI().getRawPath(),
                status,
                decision.decision(),
                decision.reason(),
                decision.effectiveLimit(),
                decision.remainingTokens(),
                decision.abuseScore(),
                decision.errorRate()
        );
    }

    private AbuseTrafficEvent event(ServerWebExchange exchange, ClientIdentity identity, RateLimitDecision decision, int status) {
        return new AbuseTrafficEvent(
                Instant.now(),
                exchange.getRequest().getId(),
                routeId(exchange),
                identity.subjectKey(),
                identity.tenantId(),
                identity.clientIp(),
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getURI().getRawPath(),
                status,
                decision.decision(),
                decision.reason(),
                decision.effectiveLimit(),
                decision.remainingTokens(),
                decision.abuseScore(),
                decision.errorRate()
        );
    }

    private void incrementMetrics(RateLimitDecision decision, int status) {
        Counter.builder("gateway_rate_limit_decisions_total")
                .tag("decision", decision.decision().name())
                .tag("reason", decision.reason().name())
                .tag("status", Integer.toString(status))
                .register(meterRegistry)
                .increment();
    }

    private void writeHeaders(ServerHttpResponse response, RateLimitDecision decision) {
        HttpHeaders headers = response.getHeaders();
        headers.set("X-RateLimit-Limit", Integer.toString(decision.effectiveLimit()));
        headers.set("X-RateLimit-Remaining", Integer.toString(decision.remainingTokens()));
        headers.set("X-RateLimit-Reset", Long.toString(decision.resetSeconds()));
        headers.set("X-RateLimit-Decision", decision.decision().name());
        headers.set("X-RateLimit-Reason", decision.reason().name());
    }

    private Mono<Void> writeProblemBody(ServerWebExchange exchange, int status, RateLimitDecision decision) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = """
                {"type":"https://example.com/problems/rate-limit","title":"Gateway protection rejected the request","status":%d,"decision":"%s","reason":"%s","requestId":"%s","retryAfterSeconds":%d}
                """.formatted(status, decision.decision().name(), decision.reason().name(),
                escape(exchange.getRequest().getId()), decision.resetSeconds());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private boolean isAdminOrActuator(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.startsWith("/admin") || path.startsWith("/actuator");
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? null : route.getId();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
