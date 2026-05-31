# Changelog

## 1.1.0

Production-grade hardening pass.

### Added

- Atomic Redis Lua fixed-window limiter with fallback path.
- Route-specific throttling policies.
- Trusted proxy CIDR validation for forwarded headers.
- Rolling multi-bucket error-rate calculation.
- Fail-open/fail-closed protection dependency mode.
- Problem JSON response for blocked and rate-limited requests.
- Top abusive routes dashboard endpoint.
- Prometheus alert rules and Grafana dashboard JSON.
- Dockerfile, Kubernetes deployment/service manifest, k6 abuse test.
- Cache eviction after access-list disable.

### Changed

- Spring Cloud release train updated to 2025.1.1 for Spring Boot 4.0.x alignment.
- Kafka abuse-events topic now uses append-only retention instead of compaction.
- Redis top offender ranking increments by abuse delta instead of cumulative score.

### Validation

- JSON and YAML artifacts validated.
- Project source structure checked.
- Gradle execution requires JDK 25 + Gradle 9.1+ and was not executed inside this sandbox because only JDK 21 is installed.
