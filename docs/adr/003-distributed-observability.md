# ADR 003 — Distributed Observability

- **Status:** Proposed
- **Category:** Diagnostics and Traceability

## Context and Problem

Currently, engineers must access multiple servers and manually compare log timestamps. This makes it difficult to identify the origin of failures and determine whether an order's payment was processed before another stage failed.

EatsNow needs centralized logging and correlation across services to investigate individual orders throughout their lifecycle.

## Considered Alternatives

1. **Local Log Files:** Inexpensive and easy to implement, but difficult to correlate across services.
2. **ELK Stack (Elasticsearch, Logstash, and Kibana):** Provides advanced log search and analysis, but may require considerable resources and maintenance.
3. **OpenTelemetry + Grafana Loki + Grafana:** OpenTelemetry provides instrumentation and context propagation, Loki centralizes logs, and Grafana supports querying and visualization. A compatible distributed tracing backend, such as Grafana Tempo or Jaeger, can be added when required.

## Decision

Adopt OpenTelemetry for instrumentation and context propagation, Grafana Loki for centralized log storage, and Grafana for visualization.

If end-to-end distributed trace visualization is required, a compatible tracing backend, such as Grafana Tempo or Jaeger, must be selected. Until that decision is made, full distributed trace visualization will not be considered a confirmed capability.

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
- Loki does not replace a distributed tracing backend on its own.
- Sensitive data, full card numbers, and credentials must not be included in logs.

These risks will be mitigated through a structured logging schema, retention policies, trace sampling, and access controls.

## Final Justification

This combination allows the team to investigate an order end to end without manually reviewing multiple log files. It is a modular solution that can start with a small number of components and expand as EatsNow's needs grow.
