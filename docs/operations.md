# Operations

## First run

```bash
docker compose up -d postgres redis kafka whoami
gradle clean test bootRun
```

## Abuse simulation

```bash
k6 run load-test/k6-abuse-test.js
```

Watch:

```bash
curl -H 'X-Admin-Token: local-admin-token-change-me' \
  'http://localhost:8080/admin/dashboard/top-tenants?window=PT1H&limit=10'

curl -H 'X-Admin-Token: local-admin-token-change-me' \
  'http://localhost:8080/admin/dashboard/redis-scores?type=tenant&limit=10'
```

## Block emergency offender

```bash
curl -X POST http://localhost:8080/admin/access-list \
  -H 'X-Admin-Token: local-admin-token-change-me' \
  -H 'Content-Type: application/json' \
  -d '{"subjectType":"TENANT","subjectValue":"abusive-tenant","mode":"BLOCK","reason":"Incident response"}'
```

## Alerts to enable

- `GatewayRateLimitedSpike`: detects mass throttling.
- `GatewayProtectionInfrastructureErrors`: detects Redis/PostgreSQL/Kafka fallback path.
- Gateway p99 latency by route from `spring_cloud_gateway_requests_seconds_bucket`.

## Tuning checklist

1. Start with route-level limits for high-cost endpoints such as login, OTP, search, exports, and file upload.
2. Keep tenant limits higher than anonymous/IP limits.
3. Keep allowlist bypass disabled for public clients unless business-critical.
4. Prefer short Redis windows for low p99 and fast recovery.
5. Review top offenders daily from PostgreSQL dashboards and Redis scores.
6. Keep audit sampling low for healthy traffic, but persist all blocked/rate-limited/error traffic.
