# Load test environment

- Recorded (UTC): 2026-11-11T00:15:49Z
- Tested commit: 243ea451fe12808696278feea783ca2801d38faf (branch feat/Machado), with uncommitted changes
- Compose file: docker-compose.yml at that commit
- Machine: AMD Ryzen 7 7730U with Radeon Graphics, 8 cores / 16 threads, 39.4 GB RAM
- Operating system: Microsoft Windows 11 Pro 10.0.26200
- Docker engine (all containers share it): 16 CPUs, 19.2 GB, Docker 29.9.2
- Load generator: k6 (grafana/k6:0.54.0) in the compose network, calling the Gateway
- Dataset: 2 restaurants, 5 menu items, 1 drivers, 3713 orders before the run
- Users and payments: synthetic customer alex@example.test, synthetic payment token tok_ok (payment test double)

| Container | Image |
| --- | --- |
| eatsnow-alloy-1 | grafana/alloy:v1.20.1 |
| eatsnow-catalog-1 | eatsnow-catalog |
| eatsnow-delivery-1 | eatsnow-delivery |
| eatsnow-gateway-1 | 6e3e429e9b67 |
| eatsnow-grafana-1 | grafana/grafana:11.3.0 |
| eatsnow-grafana-renderer-1 | grafana/grafana-image-renderer:3.11.6 |
| eatsnow-identity-1 | eatsnow-identity |
| eatsnow-jaeger-1 | jaegertracing/jaeger:2.22.0 |
| eatsnow-loki-1 | grafana/loki:3.2.1 |
| eatsnow-mailpit-1 | axllent/mailpit:v1.21 |
| eatsnow-notifications-1 | eatsnow-notifications |
| eatsnow-orders-1 | eatsnow-orders |
| eatsnow-otel-collector-1 | otel/opentelemetry-collector-contrib:0.114.0 |
| eatsnow-payments-1 | eatsnow-payments |
| eatsnow-postgres-1 | postgres:16-alpine |
| eatsnow-postgres-exporter-1 | quay.io/prometheuscommunity/postgres-exporter:v0.15.0 |
| eatsnow-prometheus-1 | prom/prometheus:v2.55.1 |
| eatsnow-rabbitmq-1 | rabbitmq:3.13-management-alpine |
| eatsnow-redis-1 | redis:7-alpine |
| eatsnow-redis-exporter-1 | oliver006/redis_exporter:v1.66.0 |
| eatsnow-restaurants-1 | eatsnow-restaurants |
| eatsnow-tempo-1 | grafana/tempo:2.6.1 |
