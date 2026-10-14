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

`Created -> Accepted -> Preparing -> OnTheWay -> Delivered`

Exception flows include:

- `PaymentFailed`
- `Rejected`
- `Cancelled`
- `DeliveryFailed`

State transitions are expected to be event-driven, validated, and auditable.

## 8) High-Level Event Contracts

Representative domain events:

- `OrderCreated`
- `PaymentAuthorized` / `PaymentFailed`
- `RestaurantAccepted` / `RestaurantRejected`
- `OrderPreparing`
- `DeliveryAssigned`
- `OrderOnTheWay`
- `OrderDelivered`

Minimum event metadata (high level):

- `eventId`
- `eventType`
- `occurredAt`
- `correlationId`
- `causationId`
- `aggregateId`
- `payload`

## 9) Technology Stack (Planned/Selected)

- **Message broker:** RabbitMQ
- **Databases:** PostgreSQL
- **Cache:** Redis
- **Containerization:** Docker / Docker Compose
- **Observability:** Prometheus, Loki, Grafana, OpenTelemetry (Jaeger/Tempo backend)
- **Architecture method:** C4 + ADRs

Final implementation choices must remain aligned with the official technical specification and ADRs.

## 10) Repository Structure (Current)

```text
.
├── README.md
└── docs/
    ├── adr/
    │   └── Orquestación event-driven.md
    └── assets/
        ├── modelocagp6.drawio
        └── modelocagp6.drawio.svg
```

## 11) Documentation Links and Locations

- **Repository:** <https://github.com/TheSpartan0110/ARQ2-2026-TS-006>
- **ADRs folder (current):** `/docs/adr/`
- **Architecture assets (current):** `/docs/assets/`
- **Technical Specification (Google Docs):** **`[TS-006 LINK PENDING - INSERT GOOGLE DOCS URL HERE]`**

If additional design artifacts are added (e.g., OpenAPI/OpenSpec files), they should remain under `/docs/` with clear versioning.

## 12) ADR Summary (Current)

Current ADR work indicates an event-driven orchestration direction, centered on:

1. **Asynchronous messaging** to reduce runtime coupling.
2. **Read optimization/caching** for restaurant catalog access.
3. **Traceability-first design** with distributed observability.

As ADRs are formalized, each record should capture context, decision, alternatives, and consequences.

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
- [ ] Formal ADR set completed and standardized
- [ ] Contract specification finalized
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
