package com.github.dimitryivaniuta.gateway.ratelimit;

import com.github.dimitryivaniuta.gateway.domain.WindowResult;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Redis-backed fixed-window limiter optimized for one atomic Redis Lua execution.
 *
 * <p>The Lua path avoids the classic INCR-then-EXPIRE race where a process can
 * die after incrementing a new key but before setting TTL. A defensive fallback
 * keeps the gateway usable on Redis deployments that disable script execution.</p>
 */
@Component
@RequiredArgsConstructor
public class RedisFixedWindowRateLimiter {

    private static final String PREFIX = "arl:bucket:";
    private static final RedisScript<List> WINDOW_SCRIPT = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                ttl = tonumber(ARGV[2])
            end
            local remaining = tonumber(ARGV[1]) - current
            if remaining < 0 then remaining = 0 end
            return { current, remaining, ttl }
            """, List.class);

    private final ReactiveStringRedisTemplate redis;
    private final Clock clock = Clock.systemUTC();

    /**
     * Consumes one request token for a subject key.
     *
     * @param subjectKey stable client key
     * @param limit effective limit for the window
     * @param window window duration
     * @return window result
     */
    public Mono<WindowResult> tryConsume(String subjectKey, int limit, Duration window) {
        long windowSeconds = Math.max(1, window.toSeconds());
        long windowId = clock.instant().getEpochSecond() / windowSeconds;
        String key = PREFIX + subjectKey + ':' + windowSeconds + ':' + windowId;
        Duration ttl = window.plusSeconds(5);
        List<String> args = List.of(Integer.toString(limit), Long.toString(Math.max(1000L, ttl.toMillis())));
        return redis.execute(WINDOW_SCRIPT, List.of(key), args)
                .next()
                .map(values -> toResult(values, limit))
                .switchIfEmpty(fallbackIncrement(key, limit, ttl))
                .onErrorResume(ex -> fallbackIncrement(key, limit, ttl));
    }

    private Mono<WindowResult> fallbackIncrement(String key, int limit, Duration ttl) {
        return redis.opsForValue().increment(key)
                .flatMap(count -> ensureTtl(key, count, ttl).thenReturn(count))
                .flatMap(count -> redis.getExpire(key)
                        .defaultIfEmpty(Duration.ZERO)
                        .map(expire -> toResult(count == null ? 0 : count, limit, expire)));
    }

    private Mono<Boolean> ensureTtl(String key, Long count, Duration ttl) {
        if (count != null && count == 1L) {
            return redis.expire(key, ttl);
        }
        return Mono.just(Boolean.TRUE);
    }

    private WindowResult toResult(List values, int limit) {
        long count = number(values, 0);
        int remaining = (int) Math.max(0, number(values, 1));
        long resetSeconds = Math.max(0, number(values, 2) / 1000);
        return new WindowResult(count <= limit, count, remaining, resetSeconds);
    }

    private WindowResult toResult(long count, int limit, Duration ttl) {
        boolean allowed = count <= limit;
        int remaining = (int) Math.max(0, limit - count);
        long resetSeconds = Math.max(0, ttl.toSeconds());
        return new WindowResult(allowed, count, remaining, resetSeconds);
    }

    private long number(List values, int index) {
        if (values == null || values.size() <= index || values.get(index) == null) {
            return 0;
        }
        Object value = values.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
