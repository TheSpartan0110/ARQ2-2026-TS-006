# ADR 006 — Asynchronous Customer Notifications

- **Status:** Proposed
- **Category:** Microservices Integration

## Context and Problem

Customers must be informed when their order is accepted, on the way, delivered, rejected, when payment fails and when a refund is confirmed. In the original design, side effects such as notifications were part of the synchronous chain, so a slow external provider could delay or break order processing. Notifications must never block order creation or state transitions, and a redelivered message must not send the same email twice.

## Considered Alternatives

1. **Each service sends its own emails synchronously:** no extra service, but couples every service to the email provider and lets provider latency affect business transactions.
2. **Notifications inside the Order Service:** fewer containers, but mixes orchestration with an external, failure-prone integration.
3. **Dedicated Notification Service consuming order events, with an SMTP adapter:** isolates the external provider behind an asynchronous consumer.

For the email provider, Amazon SES and SendGrid were considered for production. Both are reached through standard SMTP, so the adapter stays provider-neutral.

## Decision

Introduce a Notification Service that consumes only the `OrderStatusChanged` event published by the Order Service from the `notifications.events` queue. It resolves the customer's email through an internal Identity endpoint, records one Notification per `(orderId, trigger)` in the `notifications` schema to prevent duplicates, and sends the email through an SMTP adapter. Wave 2 uses a local SMTP test server (Mailpit); production can use Amazon SES over SMTP.

Provider failures are retried with the standard retry policy and dead-letter queue. A failed notification is recorded and measured, but it never changes the order.

## Trade-offs

### Positives (What We Gain)

- Email provider latency or outages cannot affect order creation or transitions.
- A single consumer of one event type keeps the notification rules in one place.
- Deduplication guarantees at most one email per trigger even with at-least-once delivery.
- SMTP keeps the provider replaceable and testable locally.

### Negatives (Risks / What We Lose)

- One more service and queue to deploy and monitor.
- Notifications are eventually consistent and may arrive after a short delay.
- If the provider accepts an email but the response is lost, a retry could send a second email; the Notification record limits but cannot fully eliminate this case.

These risks will be mitigated through queue and DLQ alerts, notification metrics by outcome, and recording the send attempt before calling the provider.

## Final Justification

A dedicated, event-driven Notification Service applies the same decoupling principle that solves EatsNow's core problem: external, slow or failing dependencies stay outside the order's critical path.
