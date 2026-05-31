package com.github.dimitryivaniuta.gateway.api;

import com.github.dimitryivaniuta.gateway.audit.RateLimitAuditRepository;
import com.github.dimitryivaniuta.gateway.domain.DashboardRow;
import com.github.dimitryivaniuta.gateway.domain.RedisScoreRow;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Admin dashboard API for top abusive IPs, tenants, routes, and current Redis abuse scores.
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final String TOP_IPS = "arl:abuse:top:ip";
    private static final String TOP_TENANTS = "arl:abuse:top:tenant";

    private final RateLimitAuditRepository repository;
    private final ReactiveStringRedisTemplate redis;

    /**
     * Returns top abusive IP addresses from the persistent audit table.
     */
    @GetMapping("/top-ips")
    public Flux<DashboardRow> topIps(@RequestParam(defaultValue = "PT1H") Duration window,
                                     @RequestParam(defaultValue = "10") int limit) {
        return repository.topIps(from(window), safeLimit(limit));
    }

    /**
     * Returns top abusive tenants from the persistent audit table.
     */
    @GetMapping("/top-tenants")
    public Flux<DashboardRow> topTenants(@RequestParam(defaultValue = "PT1H") Duration window,
                                         @RequestParam(defaultValue = "10") int limit) {
        return repository.topTenants(from(window), safeLimit(limit));
    }

    /**
     * Returns routes causing the most rejected/error traffic.
     */
    @GetMapping("/top-routes")
    public Flux<DashboardRow> topRoutes(@RequestParam(defaultValue = "PT1H") Duration window,
                                        @RequestParam(defaultValue = "10") int limit) {
        return repository.topRoutes(from(window), safeLimit(limit));
    }

    /**
     * Returns current Redis sorted-set abuse scores for IPs or tenants.
     */
    @GetMapping("/redis-scores")
    public Flux<RedisScoreRow> redisScores(@RequestParam(defaultValue = "ip") String type,
                                           @RequestParam(defaultValue = "10") int limit) {
        String key = "tenant".equalsIgnoreCase(type) ? TOP_TENANTS : TOP_IPS;
        return redis.opsForZSet()
                .reverseRangeWithScores(key, 0, safeLimit(limit) - 1L)
                .map(this::toRow);
    }

    private RedisScoreRow toRow(ZSetOperations.TypedTuple<String> tuple) {
        return new RedisScoreRow(tuple.getValue(), tuple.getScore() == null ? 0.0 : tuple.getScore());
    }

    private OffsetDateTime from(Duration window) {
        Duration safeWindow = window.isNegative() || window.isZero() ? Duration.ofHours(1) : window;
        return OffsetDateTime.now(ZoneOffset.UTC).minus(safeWindow);
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(100, limit));
    }
}
