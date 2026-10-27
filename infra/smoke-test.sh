#!/usr/bin/env bash
# Verifies the Step 1 infrastructure after `docker compose up -d`.
# Usage: ./infra/smoke-test.sh
FAIL=0
ok()   { echo "  [ OK ] $1"; }
bad()  { echo "  [FAIL] $1"; FAIL=1; }
check(){ if eval "$2" >/dev/null 2>&1; then ok "$1"; else bad "$1"; fi; }

echo "PostgreSQL"
check "7 service schemas exist" \
  "[ \"\$(docker compose exec -T postgres psql -U postgres -d eatsnow -tAc \"SELECT count(*) FROM information_schema.schemata WHERE schema_name IN ('identity','catalog','orders','payments','restaurants','deliveries','notifications')\")\" = 7 ]"
check "orders_svc CANNOT use schema payments" \
  "[ \"\$(docker compose exec -T postgres psql -U postgres -d eatsnow -tAc \"SELECT has_schema_privilege('orders_svc','payments','USAGE')\")\" = f ]"

echo "Redis"
check "PING" "[ \"\$(docker compose exec -T redis redis-cli ping)\" = PONG ]"

echo "RabbitMQ"
RMQ="curl -sf -u eatsnow_admin:admin_dev_pw http://localhost:15672/api"
check "5 main queues + 5 DLQs + 15 retry queues (25)" "[ \"\$($RMQ/queues | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')\" = 25 ]"
check "exchanges eatsnow.events.v1 / commands.v1 / dlx.v1 / retry.v1" \
  "$RMQ/exchanges/%2F | python3 -c 'import sys,json;n={e[\"name\"] for e in json.load(sys.stdin)};sys.exit(0 if {\"eatsnow.events.v1\",\"eatsnow.commands.v1\",\"eatsnow.dlx.v1\",\"eatsnow.retry.v1\"}<=n else 1)'"
check "Prometheus metrics endpoint (15692)" "curl -sf http://localhost:15692/metrics | grep -q rabbitmq_queue_messages_ready"

echo "Observability"
check "Prometheus ready"   "curl -sf http://localhost:9090/-/ready"
check "Prometheus rules loaded (8 alerts)" \
  "[ \"\$(curl -sf http://localhost:9090/api/v1/rules | python3 -c 'import sys,json;print(sum(len(g[\"rules\"]) for g in json.load(sys.stdin)[\"data\"][\"groups\"]))')\" = 8 ]"
check "Loki ready"         "curl -sf http://localhost:3100/ready"
check "Collector metrics"  "docker compose exec -T prometheus wget -qO- http://otel-collector:8888/metrics"
GRAFANA="curl -sf -u ${GRAFANA_ADMIN_USER:-admin}:${GRAFANA_ADMIN_PASSWORD:-admin_dev_pw} http://localhost:3000/api"
check "Grafana datasource Prometheus healthy" "$GRAFANA/datasources/uid/prometheus/health"
check "Grafana datasource Loki healthy"       "$GRAFANA/datasources/uid/loki/health"
check "Grafana datasource Tempo healthy"      "$GRAFANA/datasources/uid/tempo/health"
check "Grafana dashboard provisioned"         "$GRAFANA/dashboards/uid/eatsnow-infra"

echo
[ $FAIL -eq 0 ] && echo "All checks passed." || echo "Some checks failed: run 'docker compose ps' and 'docker compose logs <service>'."
exit $FAIL
