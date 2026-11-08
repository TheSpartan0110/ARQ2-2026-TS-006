# ADR 003 — Distributed Observability

- **Status:** Accepted (implemented in Wave 2)
- **Category:** Diagnostics and Traceability

## Context and Problem

Currently, engineers must access multiple servers and manually compare log timestamps. This makes it difficult to identify the origin of failures and determine whether an order's payment was processed before another stage failed.

EatsNow needs centralized logging, metrics and distributed tracing across services to investigate individual orders throughout their lifecycle. For Case 6, distributed tracing (OpenTelemetry with Jaeger or Grafana Tempo) is mandatory.

## Considered Alternatives

1. **Local Log Files:** Inexpensive and easy to implement, but difficult to correlate across services.
2. **ELK Stack (Elasticsearch, Logstash, and Kibana):** Provides advanced log search and analysis, but may require considerable resources and maintenance.
3. **OpenTelemetry + Prometheus + Grafana Loki + Grafana, with a tracing backend:** OpenTelemetry provides instrumentation and context propagation, Prometheus collects metrics, Loki centralizes logs, and Grafana supports querying and visualization. Two tracing backends were compared:
   - **Jaeger:** mature tracing backend with its own UI, but a separate tool from the Grafana dashboards used for logs and metrics.
   - **Grafana Tempo:** tracing backend queried from the same Grafana instance, with direct navigation between Loki logs and traces by `traceId`.

## Decision

Adopt OpenTelemetry for instrumentation and context propagation (SDKs in every service plus the OpenTelemetry Collector for traces), Prometheus for metrics, Grafana Loki for centralized log storage (logs shipped by Grafana Alloy through the Docker API, which works the same on Docker Desktop and Linux), Grafana Tempo for distributed traces and Grafana for dashboards and alerts.

Distributed tracing is a confirmed, mandatory capability: every HTTP request, message publication and message consumption creates spans, and W3C trace context is propagated through HTTP headers and message envelopes. Tempo is the primary trace store because it keeps logs, metrics and traces in a single Grafana interface (log <-> trace links by `traceId`, service graph), which is how operators investigate an order. The Collector also exports the same traces to Jaeger, whose UI is a dedicated trace inspector: it is the tool the team and reviewers use to inspect one trace or every trace of an order. Both receive identical data; no service depends on either backend. Telemetry contracts (log schema, metrics, alerts and sampling) are defined in Section 6 of `docs/spec.md`.

A `correlationId` will be generated for each order workflow and propagated across service calls and asynchronous messages. It is included in structured logs and as span attributes (`eatsnow.correlation_id`, `eatsnow.order_id`) on every span that handles the order, so one search returns all of its traces (creation, restaurant decisions and driver actions start separate traces). Responses return the trace id in `X-Trace-Id` to open a specific trace directly.

The system will also record the `orderId`, order state transitions, payment attempt outcomes, and errors.

## Trade-offs

### Positives (What We Gain)

- Allows engineers to investigate an order's journey using a shared identifier.
- Reduces the time needed to diagnose errors across microservices.
- Helps identify latency, failures, and retries.
- Supports dashboards and alerts to detect problems before they affect many orders.

### Negatives (Risks / What We Lose)

- Instrumentation and storage consume resources.
- Logs from all services must follow a consistent format.
- The stack adds several containers (Collector, Alloy, Prometheus, Loki, Tempo, Jaeger, Grafana) to operate. Jaeger keeps traces in memory in Wave 2, so they are lost when its container restarts; Tempo remains the persistent store.
- Sensitive data, full card numbers, and credentials must not be included in logs or traces.

These risks will be mitigated through a structured logging schema, retention policies, trace sampling, access controls and asynchronous telemetry export that never blocks order processing.

## Final Justification

This combination allows the team to investigate an order end to end without manually reviewing multiple log files. It is a modular solution that can start with a small number of components and expand as EatsNow's needs grow.
