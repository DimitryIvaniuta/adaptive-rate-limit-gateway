# Architecture

## Core flow

```text
Client
  -> trusted load balancer / reverse proxy
  -> Spring Cloud Gateway WebFlux
  -> AdaptiveRateLimitFilter
      -> ClientIdentityResolver
      -> AccessListService
      -> RateLimitPolicyResolver
      -> AbuseScoreService + TrafficStatsService
      -> RedisFixedWindowRateLimiter
  -> upstream route
```

## Identity model

The gateway resolves identity in this order:

1. `X-Tenant-Id` if present.
2. SHA-256 hash of `X-Api-Key` if present.
3. Client IP.

`X-Forwarded-For` and `X-Real-IP` are only trusted when the direct remote address matches `gateway.trusted-proxy-cidrs`.

## Rate-limit model

The hot path uses a Redis fixed-window counter. The counter is incremented and assigned TTL through one Lua script execution.

Dynamic limits are computed from:

- global or route-specific base limit,
- rolling error rate from recent Redis buckets,
- Redis abuse score,
- minimum limit floor,
- hard-block score threshold.

## Data stores

| Store | Purpose |
|---|---|
| Redis | Hot counters, abuse scores, access-list cache, top-score sorted sets |
| PostgreSQL | Access-list source of truth and audit dashboard history |
| Kafka | Append-only abuse/rate-limit events |
| Prometheus | Gateway metrics and p99 latency |

## Failure mode

`gateway.rate-limit.fail-open=true` lets normal traffic continue if a protection dependency fails. The gateway records a `PROTECTION_INFRASTRUCTURE_ERROR` metric/event so operations can alert quickly. Set it to `false` for highly sensitive systems where strict protection is more important than availability.
