# EatsNow — Delivery and Order Orchestration Platform

![Status](https://img.shields.io/badge/status-Wave%201%20Design-blue)
![Architecture](https://img.shields.io/badge/architecture-event--driven-purple)
![Language](https://img.shields.io/badge/repository-English%20only-green)

EatsNow is a Software Architecture II (Case 6) project focused on redesigning food delivery order processing from synchronous coupling to a resilient, event-driven model.

**Repository:** <https://github.com/TheSpartan0110/ARQ2-2026-TS-006>

> **English-only policy:** all repository deliverables and technical artifacts must be written in English.

## 1) Problem Context

The original flow tightly coupled synchronous calls across multiple services:

`Customer -> Orders -> Payments -> Restaurants -> Drivers`

This created blocking behavior, brittle error propagation, and difficult end-to-end traceability. A slow or failing downstream dependency could delay or break order creation, while observability was fragmented.

## 2) Project Objectives

- Decouple order initiation from downstream processing.
- Ensure resilient asynchronous orchestration of order states.
- Keep catalog queries independent and low latency.
- Prevent duplicate effects with idempotent event handling.
- Enable end-to-end observability with correlation IDs.
- Keep contracts and architecture decisions explicit and auditable.

## 3) Mandatory Features (Case Requirements)

1. **Centralized profile and access management** for customers, restaurants, and drivers.
2. **Independent restaurant catalog queries** with read optimization (Redis) separated from transactional processing.
3. **Event-driven order creation** (acknowledge quickly, process asynchronously).
4. **Asynchronous order state lifecycle** with valid state transitions and history.
5. **Correlation-based observability** across HTTP, messaging, logs, and traces.

## 4) Team

- Sebastian Castañeda Florez
- Cristian Camilo Castro Restrepo
- Jean Pierre Ortiz Arango
- Walter Alexander Pino Vasquez
- Juan David Machado Mosquera

**Team name:** Arquitectos del Caos  
**Course case:** Software Architecture II — Case 6 (EatsNow)

## 5) Architecture Overview (Wave 1 Design)

Wave 1 defines an event-driven microservices architecture:

- **Single external entry point:** API Gateway.
- **Core domain services:** Order, Catalog, Payment, Restaurant, Delivery, Notification, Identity/Access.
- **Async backbone:** durable messaging with RabbitMQ.
- **Data strategy:** service-owned persistence with PostgreSQL and Redis for critical read performance.
- **Observability:** metrics, logs, and distributed tracing with correlation propagation.

> Current repository state is **design/documentation-first**. Wave 2 implementation services are planned and should not be assumed complete.

## 6) Core Services (Responsibilities)

- **API Gateway:** routing, policy enforcement, authentication checks, correlation propagation.
- **Identity & Access:** profiles, roles, authentication/authorization.
- **Order Service:** order aggregate ownership, lifecycle state management, event publication.
- **Catalog Service:** restaurant/menu read model with Redis acceleration.
- **Payment Service:** authorization and payment result events with idempotency controls.
- **Restaurant Service:** acceptance/rejection and preparation updates.
- **Delivery Service:** driver assignment and delivery progression.
- **Notification Service:** asynchronous customer/operational notifications.

## 7) Asynchronous Order Lifecycle

Primary business progression:

`CREATED -> ACCEPTED -> PREPARING -> ON_THE_WAY -> DELIVERED`

Exception flows:

- `PAYMENT_FAILED`: the payment provider definitively refused the charge.
- `REJECTED`: the restaurant rejected a paid order; the payment is refunded asynchronously.

Customer cancellation and failed deliveries are out of scope for this version. Only order creation is HTTP-driven; every later transition is triggered by an asynchronous event and validated against the state rules in `docs/spec.md`.

## 8) High-Level Event Contracts

The Order Service coordinates the workflow by consuming events and sending commands through RabbitMQ:

- **Commands:** `ChargePayment`, `RefundPayment`, `RequestRestaurantAcceptance`, `AssignDelivery`
- **Events:** `OrderCreated`, `PaymentAuthorized`, `PaymentFailed`, `PaymentPendingReconciliation`, `PaymentRefunded`, `RestaurantAccepted`, `RestaurantRejected`, `RestaurantPreparing`, `DeliveryAssigned`, `DeliveryPickedUp`, `DeliveryCompleted`, `OrderStatusChanged`

Every message uses a common envelope with `messageId`, `messageType`, `schemaVersion`, `occurredAt`, `producer`, `orderId`, `correlationId`, `causationId`, `aggregateId`, `aggregateVersion`, `traceparent` and `payload`. The full catalog is in Section 4 of `docs/spec.md`.

## 9) Technology Stack (Planned/Selected)

- **API Gateway:** Spring Cloud Gateway
- **Services:** Spring Boot
- **Identity:** Spring Authorization Server (OIDC, JWT)
- **Message broker:** RabbitMQ
- **Database:** PostgreSQL (one schema per service)
- **Cache:** Redis
- **Containerization:** Docker / Docker Compose
- **Observability:** OpenTelemetry, Prometheus, Grafana Loki, Grafana Tempo, Grafana
- **Architecture method:** C4 + ADRs

Final implementation choices must remain aligned with the official technical specification and ADRs.

## 10) Repository Structure (Current)

```text
.
├── README.md
└── docs/
    ├── spec.md
    ├── adrs/
    │   ├── 001-message-broker.md
    │   ├── 002-catalog-cache.md
    │   ├── 003-distributed-observability.md
    │   ├── 004-postgresql-schema-per-service.md
    │   ├── 005-identity-and-access.md
    │   └── 006-customer-notifications.md
    └── assets/
        ├── Modeloc4gp6.drawio.png
        └── Borrador_Modeloc4gp6.drawio.png
```

## 11) Documentation Links and Locations

- **Repository:** <https://github.com/TheSpartan0110/ARQ2-2026-TS-006>
- **Contract specification (OpenSpec):** [`docs/spec.md`](docs/spec.md)
- **ADRs:** [`docs/adrs/`](docs/adrs/)
- **C4 diagrams:** [`docs/assets/`](docs/assets/)
- **Technical Specification (Google Docs):** **`[TS-006 LINK PENDING - INSERT GOOGLE DOCS URL HERE]`**

## 12) ADR Summary

| ADR | Decision | Main alternatives considered |
| --- | --- | --- |
| [001](docs/adrs/001-message-broker.md) | RabbitMQ with Transactional Outbox and idempotent consumers | Synchronous REST, Apache Kafka |
| [002](docs/adrs/002-catalog-cache.md) | Redis cache-aside for the catalog | Direct PostgreSQL queries, local cache per instance |
| [003](docs/adrs/003-distributed-observability.md) | OpenTelemetry, Prometheus, Loki, Tempo and Grafana | Local log files, ELK Stack, Jaeger |
| [004](docs/adrs/004-postgresql-schema-per-service.md) | Shared PostgreSQL with one schema per service | One instance per service, MongoDB |
| [005](docs/adrs/005-identity-and-access.md) | Spring Authorization Server inside Identity & Access | Custom JWT login, Keycloak |
| [006](docs/adrs/006-customer-notifications.md) | Asynchronous Notification Service with an SMTP adapter | Synchronous emails per service, notifications inside Orders |

## 13) Observability and Correlation IDs

Observability is mandatory. Each order flow should propagate a `correlationId` across:

- API Gateway requests/responses
- Inter-service communication
- Broker message headers/metadata
- Structured logs
- Distributed traces

This enables complete order journey reconstruction and faster incident diagnosis.

## 14) Development Roadmap

### Wave 1 — Planning and Design (current focus)

- [x] Case assignment and scope definition
- [x] Event-driven architectural direction documented
- [x] Initial architecture artifacts in repository
- [x] Formal ADR set completed and standardized
- [x] Contract specification completed (pending team review)
- [ ] Final C4 container-level documentation consolidated

### Wave 2 — Implementation (planned)

- [ ] Implement gateway and core services
- [ ] Configure PostgreSQL, Redis, RabbitMQ
- [ ] Add asynchronous workflows and idempotent consumers
- [ ] Integrate observability stack end-to-end
- [ ] Validate implementation against specification

## 15) Getting Started

### Prerequisites

- Git
- Docker
- Docker Compose

### Clone

```bash
git clone https://github.com/TheSpartan0110/ARQ2-2026-TS-006.git
cd ARQ2-2026-TS-006
```

### Run (when Wave 2 services are available)

```bash
docker compose up --build
```

### Stop

```bash
docker compose down
```

## 16) Project Requirements Alignment

This repository is aligned with course expectations by documenting:

- Event-driven architecture for orchestration.
- API gateway-centric access pattern.
- Messaging, storage, and caching strategy.
- Architectural decisions and design artifacts.
- English-language deliverables.

## 17) Academic Context

**Course:** Software Architecture II  
**Case:** 6 — EatsNow: Delivery and Order Orchestration  
**Team:** Arquitectos del Caos

This repository currently reflects **Wave 1 design status** and serves as the architectural baseline for Wave 2 implementation.
