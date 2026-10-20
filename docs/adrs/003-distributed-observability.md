# ADR 003 — Distributed Observability

- **Status:** Proposed
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

Adopt OpenTelemetry for instrumentation and context propagation (SDKs in every service plus the OpenTelemetry Collector), Prometheus for metrics, Grafana Loki for centralized log storage, Grafana Tempo for distributed traces and Grafana for dashboards and alerts.

Distributed tracing is a confirmed, mandatory capability: every HTTP request, message publication and message consumption creates spans, and W3C trace context is propagated through HTTP headers and message envelopes. Tempo is chosen over Jaeger because it keeps logs, metrics and traces in a single Grafana interface, which is how operators investigate an order. Telemetry contracts (log schema, metrics, alerts and sampling) are defined in Section 6 of `docs/spec.md`.

A `correlationId` will be generated for each order workflow and propagated across service calls and asynchronous messages. It will be included in structured logs and trace metadata where applicable.

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
- The stack adds several containers (Collector, Prometheus, Loki, Tempo, Grafana) to operate.
- Sensitive data, full card numbers, and credentials must not be included in logs or traces.

These risks will be mitigated through a structured logging schema, retention policies, trace sampling, access controls and asynchronous telemetry export that never blocks order processing.

## Final Justification

This combination allows the team to investigate an order end to end without manually reviewing multiple log files. It is a modular solution that can start with a small number of components and expand as EatsNow's needs grow.
