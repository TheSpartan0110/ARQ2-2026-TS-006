# ADR 004 — PostgreSQL with One Schema per Service

- **Status:** Proposed
- **Category:** Data Management

## Context and Problem

Each EatsNow microservice must own its data so that services can evolve and fail independently. The order workflow also needs transactional guarantees: an order, its idempotency record and its outbox entry must be committed atomically, and payments must never be charged twice. At the same time, catalog reads must not be slowed down by purchasing operations, and the Wave 2 environment runs on a single machine with Docker Compose.

## Considered Alternatives

1. **One PostgreSQL instance per service:** strongest isolation, but multiplies containers, memory and operational work in the Wave 2 environment.
2. **MongoDB (document database) per service:** flexible schemas, but the workflow relies on multi-row transactions, unique constraints (one payment per order, one order per quote) and conditional updates that are simpler and better proven in a relational database.
3. **Single shared PostgreSQL instance with one schema and one database user per service:** keeps relational guarantees and data ownership with a single database container.

## Decision

Use a single PostgreSQL instance with one schema per service (`identity`, `catalog`, `orders`, `payments`, `restaurants`, `deliveries`, `notifications`), as shown in the C4 container diagram. Each service connects with its own database user that has privileges only on its own schema. No service reads or writes another service's schema; data is shared only through APIs and messages.

The Catalog Service uses an independent connection pool, and Redis (ADR 002) absorbs most catalog reads, so purchasing load does not exhaust catalog capacity. Outbox, idempotency and deduplication tables live in the schema of the service that owns them.

## Trade-offs

### Positives (What We Gain)

- ACID transactions for order, outbox and idempotency records in a single commit.
- Unique constraints and conditional (optimistic) updates enforce business invariants directly.
- Clear data ownership through schema-level permissions.
- One database container, which keeps the Docker Compose environment light.

### Negatives (Risks / What We Lose)

- The instance is a shared point of failure and of resource contention.
- Heavy queries from one service can affect the others.
- Schema permissions depend on correct configuration; a misconfigured user could break isolation.

These risks will be mitigated through per-service connection pools with limits, the Redis catalog cache, permission checks in the initialization scripts, database metrics in Prometheus, and the option to move a schema to its own instance without changing service contracts.

## Final Justification

PostgreSQL provides the transactional and consistency guarantees that the event-driven order workflow depends on. A schema per service preserves data ownership as required by the microservices architecture while keeping the Wave 2 infrastructure manageable. Because services never share tables, any schema can later move to a dedicated instance if load requires it.
