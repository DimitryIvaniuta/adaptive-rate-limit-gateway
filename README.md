# adaptive-rate-limit-gateway

Production-grade **API Gateway with Adaptive Rate Limits**.

## GitHub repository

**Repository name:** `adaptive-rate-limit-gateway`  
**Description:** `Spring Cloud Gateway with Redis-backed adaptive rate limits, PostgreSQL abuse dashboards, trusted proxy identity resolution, route-specific throttling, and Kafka abuse events.`

## Selected environment

| Concern | Choice |
|---|---|
| Java | Java 25 toolchain |
| Runtime | Spring Boot 4.0.6 |
| Gateway | Spring Cloud Gateway Server WebFlux via Spring Cloud 2025.1.1 |
| Reactive stack | WebFlux + Netty + R2DBC |
| Distributed rate state | Redis atomic Lua fixed-window limiter |
| Persistence | PostgreSQL + Flyway + R2DBC |
| Abuse events | Kafka in KRaft mode |
| Observability | Actuator + Prometheus + Grafana dashboard JSON |
| Build | Gradle Groovy DSL, Gradle 9.1+ recommended for Java 25 |

## What was improved in v1.1.0

- Replaced non-atomic Redis `INCR` + `EXPIRE` hot path with an atomic Lua script and safe fallback.
- Added route-specific policies via `gateway.rate-limit.route-policies`.
- Added trusted-proxy CIDR validation so clients cannot spoof `X-Forwarded-For` unless the direct peer is trusted.
- Changed recent error-rate calculation from current-minute only to a rolling multi-bucket window.
- Added fail-open/fail-closed switch for protection dependency errors.
- Added RFC-style `application/problem+json` responses for `403` and `429` decisions.
- Fixed Redis top-offender sorted-set scoring to increment by abuse delta, not cumulative score.
- Added dashboard endpoint for top abusive routes.
- Added Prometheus alert rules, Grafana dashboard JSON, Dockerfile, Kubernetes manifest, and k6 abuse scenario.
- Changed Kafka abuse topic to append-only retention instead of log compaction.
- Added cache eviction when an access-list entry is disabled.

## Architecture

Request path:

```text
Client -> Trusted LB/Proxy -> Spring Cloud Gateway -> AdaptiveRateLimitFilter -> upstream service
                                               |-> Redis: atomic counters, scores, access-list cache
                                               |-> PostgreSQL: access lists and audit dashboards
                                               |-> Kafka: abuse/rate-limit event stream
                                               |-> Micrometer/Prometheus: metrics and p99 latency
```

### Adaptive rate-limit model

1. Resolve identity from `X-Tenant-Id`, `X-Api-Key`, trusted `X-Forwarded-For`, and remote IP.
2. Check Redis-cached PostgreSQL access list.
3. Explicit `BLOCK` denies immediately.
4. Explicit `ALLOW` can bypass rate limiting when `allowlist-bypass=true`.
5. Resolve route-specific policy by Gateway route id.
6. Calculate a dynamic limit:

```text
effectiveLimit = clamp(baseLimit * (1 - errorPenalty - abusePenalty), minimumLimit, baseLimit)
```

`errorPenalty` comes from recent rolling client-side error ratio. `abusePenalty` comes from Redis abuse score. Redis fixed-window counters are executed atomically through Lua for low overhead and consistent TTL.

## Run locally

```bash
docker compose up -d postgres redis kafka whoami
gradle clean test bootRun
```

The default gateway routes proxy:

```text
GET http://localhost:8080/api/**  -> http://localhost:9080/**
GET http://localhost:8080/auth/** -> http://localhost:9080/** with stricter policy
```

Example:

```bash
curl -i -H 'X-Tenant-Id: tenant-a' http://localhost:8080/api/anything
curl -i -H 'X-Tenant-Id: tenant-a' http://localhost:8080/auth/login
```

## Admin token

Admin endpoints require:

```text
X-Admin-Token: local-admin-token-change-me
```

Change it with:

```yaml
gateway:
  admin-token: ${GATEWAY_ADMIN_TOKEN}
```

For real production, replace this with OAuth2/OIDC, mTLS, or a dedicated internal admin gateway.

## Main endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/admin/access-list` | Create allowlist/blocklist entry |
| `GET` | `/admin/access-list` | List active entries |
| `DELETE` | `/admin/access-list/{id}` | Disable entry and evict cache |
| `GET` | `/admin/dashboard/top-ips` | Top abusive IPs from audit table |
| `GET` | `/admin/dashboard/top-tenants` | Top abusive tenants from audit table |
| `GET` | `/admin/dashboard/top-routes` | Routes with most rejected/error traffic |
| `GET` | `/admin/dashboard/redis-scores` | Current Redis abuse scores |
| `GET` | `/admin/policy` | Active global and route policy |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

## Route-specific policy example

```yaml
gateway:
  rate-limit:
    route-policies:
      auth-api:
        base-limit-per-minute: 30
        tenant-base-limit-per-minute: 120
        minimum-limit-per-minute: 5
        window: PT1M
        error-rate-threshold: 0.10
        abuse-score-hard-block: 60
```

## Trusted forwarded headers

Only trust `X-Forwarded-For` and `X-Real-IP` from known proxy CIDRs:

```yaml
gateway:
  trusted-forwarded-headers: true
  trusted-proxy-cidrs:
    - 10.0.0.0/8
    - 172.16.0.0/12
    - 192.168.0.0/16
```

Do not use `0.0.0.0/0` outside tests.

## Observability

Start Prometheus/Grafana profile:

```bash
docker compose --profile observability up -d prometheus grafana
```

Files:

```text
observability/prometheus.yml
observability/prometheus-rules.yml
observability/grafana-dashboard.json
```

Key metrics:

```text
gateway_rate_limit_decisions_total{decision="RATE_LIMITED"}
gateway_rate_limit_decisions_total{reason="PROTECTION_INFRASTRUCTURE_ERROR"}
spring_cloud_gateway_requests_seconds_bucket
```

## Load / abuse test

```bash
k6 run load-test/k6-abuse-test.js
```

Expected result: normal tenant traffic should stay fast while abusive `auth-api` traffic receives `429` and accumulates abuse score.

## Test with Postman

Import:

```text
postman/adaptive-rate-limit-gateway.postman_collection.json
postman/local.postman_environment.json
```

## Build container

```bash
docker build -t adaptive-rate-limit-gateway:1.1.0 .
```

## Kubernetes

```bash
kubectl create secret generic adaptive-rate-limit-gateway --from-literal=admin-token='replace-me'
kubectl apply -f k8s/deployment.yaml
```

## Production hardening notes

- Put the gateway behind a trusted load balancer and restrict forwarded headers to that layer.
- Use Redis Cluster or managed Redis with low-latency networking.
- Decide explicitly between `fail-open=true` for availability or `fail-open=false` for strict protection.
- Tune policies per route/business plan; authentication and OTP endpoints usually need lower limits.
- Partition Kafka abuse events by `tenantId` or `clientIp` depending on downstream analytics needs.
- Keep audit sampling low for healthy traffic; persist all blocked/rate-limited/error traffic.
- Add retention/partitioning to `rate_limit_audit` for very high-volume environments.
- Alert on p99 latency, protection infrastructure fallback, and sudden spikes of `RATE_LIMITED` decisions.

## Acceptance mapping

| Requirement | Implementation |
|---|---|
| Base rate limit | `gateway.rate-limit.base-limit-per-minute` and tenant base |
| Dynamic limit based on error rate/abuse score | `AdaptiveLimitCalculator`, rolling `TrafficStatsService`, `AbuseScoreService` |
| Blocklist/allowlist | PostgreSQL `access_list`, Redis cache, admin API |
| Dashboards top abusive IPs/tenants | `/admin/dashboard/top-ips`, `/admin/dashboard/top-tenants`, Redis scores |
| Low overhead | Redis Lua single-command hot path, WebFlux, R2DBC, audit sampling |
| Abuse traffic reduced | Immediate block/rate-limit, adaptive penalties, hard block threshold |
| p99 stable | Non-blocking gateway path, fail-open option, bounded side effects, Prometheus p99 dashboard |
