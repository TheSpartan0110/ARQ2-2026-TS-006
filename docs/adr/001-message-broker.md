# ADR 001 — Message Broker

- **Status:** Proposed
- **Category:** Microservices Integration

## Context and Problem

Currently, creating an order requires sequential synchronous calls to multiple microservices. If the Restaurant Service takes 30 seconds to respond, the process may time out and trigger a general error. The system needs to decouple services and allow them to process orders independently.

## Considered Alternatives

1. **Synchronous REST:** Simple to implement, but maintains temporal coupling and allows failures to propagate across services.
2. **Apache Kafka:** Provides a distributed event log, message retention, and replay capabilities, but introduces greater operational complexity for this scenario.
3. **RabbitMQ:** Supports durable queues, message acknowledgments, routing, and retries to distribute work among consumers.

## Decision

Use RabbitMQ as the message broker for domain events. The Order Service will publish `OrderCreated` and coordinate the order lifecycle through events such as `PaymentAuthorized`, `RestaurantAccepted`, and `DeliveryAssigned`. No separate Orchestrator Service is introduced in the current proposal.

The Transactional Outbox pattern and idempotent consumers will be implemented to improve message reliability and prevent duplicate processing from causing unintended effects.

## Trade-offs

### Positives (What We Gain)

- Reduces coupling between services.
- Allows the system to absorb order spikes through queues.
- Supports retries when a service is temporarily unavailable.
- Prevents order creation from depending on an immediate response from the Restaurant Service.

### Negatives (Risks / What We Lose)

- Processing is eventually consistent, so order states may take time to update.
- Duplicate messages may occur, requiring idempotent consumers.
- Queues, failed messages, monitoring, and recovery procedures must be managed.
- RabbitMQ does not eliminate all failures or guarantee exactly-once processing on its own.

These risks will be mitigated through durable messaging, acknowledgments, retry policies, dead-letter queues where appropriate, the Transactional Outbox pattern, and idempotent consumers.

## Final Justification

RabbitMQ offers an appropriate balance between messaging capabilities, integration simplicity, and operational complexity for EatsNow's order workflow. Kafka would be a valid alternative if the business required extensive event replay, multiple analytical consumers, or substantially higher event volumes.
