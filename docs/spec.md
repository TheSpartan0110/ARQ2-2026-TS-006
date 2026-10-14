# EatsNow Contract Specification

Status: Partial draft - team contributions pending | Version: 0.1.0 | Team: TS-006

## Purpose

Define the observable contracts and acceptance requirements for EatsNow, ensuring independent catalog access, asynchronous order processing, valid order transitions and end-to-end traceability.

This single-file academic deliverable remains at `docs/spec.md` as required by the assignment. The Requirements section uses OpenSpec requirement and scenario syntax; the supporting sections organize the required domain, REST and messaging contracts. This document is a baseline specification, not an OpenSpec change delta or an initialized CLI workspace.

## Contribution Status

This is an intentionally incomplete working draft, not a submission-ready specification. Pending sections require substantive contracts and examples from the team, not only approval of existing text. Contributors should record their own work through commits, as required by the assignment.

| Section | Status | Remaining contribution |
| --- | --- | --- |
| Overview, domain schemas and state rules | Drafted for review | Validate the proposed decisions with the team and ADRs. |
| Shared HTTP contract, profiles, catalog and orders | Drafted for review | Use these as examples when completing the remaining APIs. |
| 3.5 Restaurant operations | Pending - owner to be assigned | Define restaurant endpoints and examples. |
| 3.6 Drivers and deliveries | Pending - owner to be assigned | Define driver and delivery endpoints and examples. |
| 4.2 Message catalog | Pending - owner to be assigned | Define each command/event payload and routing contract. |
| Requirements | Drafted for review | Keep scenarios aligned with the completed contracts. |
| 6. Observability and verification | Pending - owner to be assigned | Define telemetry fields, metrics, alerts and verification evidence. |

Existing schemas, state rules and scenarios are proposed inputs to the pending sections. Event names and any referenced routes or thresholds must be reconciled when those sections are completed. All pending work must be finished before final submission.

## 1. Overview

This specification defines the proposed contracts for Case 6: EatsNow - Delivery and Order Orchestration. Wave 2 implementation must conform to the approved version of this file. The Tech Spec remains in Google Docs; this file contains contracts, not a copy of that document.

The system must accept an order without waiting for Payments, Restaurants or Delivery to finish processing. A restaurant outage must not turn an already persisted order into an HTTP 500 response. Operators must be able to determine the order and payment outcomes through correlated logs and distributed traces.

### 1.1 Services and ownership

| Service / boundary | Responsibility and owned data |
| --- | --- |
| API Gateway | Public HTTP entry point, token validation, routing, request limits and correlation headers. No business data ownership. |
| Identity and Access | Central user profiles, authentication, roles and links to restaurant or driver identities. |
| Catalog | Restaurant listings, menus, prices, availability and immutable checkout quotes. PostgreSQL is the source of truth; Redis is a cache. |
| Orders and Orchestration | Orders, purchased item snapshots, global order states, orchestration progress, idempotency records and outbox. The orchestrator belongs to this boundary. |
| Payments | Payment attempts, provider references, payment status, reconciliation and refunds. |
| Restaurant Operations | Restaurant order inbox, acceptance/rejection and preparation progress. This is separate from Catalog's commercial model. |
| Delivery | Driver availability, assignments, pickup and delivery progress. |
| Observability | OpenTelemetry instrumentation, Prometheus metrics, Loki logs, Grafana dashboards and Tempo distributed traces. |

Each business service owns its persistent data. No service writes another service's tables. PostgreSQL may host separate databases/users in the local environment; Catalog must have an independent connection pool. RabbitMQ transports durable messages. Services and infrastructure run through Docker Compose in Wave 2. An external payment adapter may use a local test double; real credentials or card numbers must never be committed.

### 1.2 Draft decisions requiring team approval

The existing ADR proposals establish RabbitMQ, Transactional Outbox, idempotent consumers, Redis cache-aside and OpenTelemetry/Loki/Grafana. The following details make those proposals implementable, but are not previously approved decisions:

- Use an OIDC-compatible identity provider and JWT access tokens. Provision restaurant and driver accounts through trusted administration; public role escalation is forbidden.
- Obtain a signed, short-lived quote before order submission. Orders verifies it locally, avoiding a synchronous Catalog call during order creation.
- Complete payment before requesting restaurant acceptance. If the restaurant rejects the paid order, refund it asynchronously. This choice avoids preparing an unpaid order but can require a customer refund.
- Use `PaymentSucceeded` for confirmed capture, not authorization alone. The ADR's example `PaymentAuthorized` is not sufficient evidence of a charge; final event names and payment sequencing must be aligned with the ADR before approval.
- Add `DELIVERED`, `REJECTED` and `PAYMENT_FAILED` to the four mandatory order states. Customer cancellation, partial refunds, tips, taxes, delivery fees and multiple restaurants per order are outside this draft's scope.
- Select Tempo as the mandatory trace backend for Case 6. Distributed tracing is not optional. The current observability ADR must be aligned with this assignment requirement.
- TTLs, timeouts and performance targets below are proposed acceptance criteria, not measured results.

## 2. Domain Schemas

### 2.1 Common types

All payloads use JSON with camelCase fields. Fields are required unless marked optional. IDs are UUID strings; timestamps are UTC RFC 3339 strings. Examples use valid UUIDs. Unknown request fields are rejected, except optional future fields on received events. Versions are positive integers.

| Type | Definition |
| --- | --- |
| Money | `{ "amountMinor": integer >= 0, "currency": "COP" }`. Values use minor units, never floating point. COP 25,000 is `2500000`. |
| Address | `{ "line1": string[1..200], "city": string[1..100], "instructions": string[0..300] (optional) }`. |
| Role | `USER`, `RESTAURANT`, `DRIVER`. |
| OrderStatus | `CREATED`, `ACCEPTED`, `PREPARING`, `ON_THE_WAY`, `DELIVERED`, `REJECTED`, `PAYMENT_FAILED`. |
| PaymentStatus | `PENDING`, `UNKNOWN`, `SUCCEEDED`, `FAILED`, `REFUND_PENDING`, `REFUNDED`. |
| RestaurantOrderStatus | `PENDING`, `ACCEPTED`, `REJECTED`, `PREPARING`. |
| DeliveryStatus | `UNASSIGNED`, `ASSIGNED`, `PICKED_UP`, `DELIVERED`. |
| Error | `{ "code": string, "message": string, "correlationId": UUID, "details": object (optional) }`. No internal stack traces or secrets. |

### 2.2 Business objects

| Schema | Fields and constraints |
| --- | --- |
| Profile | `id: UUID`, `displayName: string[1..100]`, `email: string(email)`, `roles: Role[1..3]`, `restaurantId: UUID?`, `driverId: UUID?`, `active: boolean`. A RESTAURANT role requires restaurantId; a DRIVER role requires driverId. |
| Restaurant | `id: UUID`, `name: string[1..120]`, `available: boolean`. |
| MenuItem | `id: UUID`, `restaurantId: UUID`, `name: string[1..120]`, `price: Money`, `available: boolean`, `version: integer`. |
| RequestedItem | `menuItemId: UUID`, `quantity: integer[1..20]`. Duplicate menuItemId values are invalid. |
| OrderItem | `menuItemId: UUID`, `name: string`, `quantity: integer[1..20]`, `unitPrice: Money`. Immutable snapshot from the quote. |
| Quote | `id: UUID`, `customerId: UUID`, `restaurantId: UUID`, `items: OrderItem[1..50]`, `total: Money`, `expiresAt: timestamp`, `quoteToken: string`. Total is the sum of quantity times unitPrice; all items share one restaurant and currency. |
| Order | `id: UUID`, `customerId: UUID`, `restaurantId: UUID`, `items: OrderItem[1..50]`, `total: Money`, `deliveryAddress: Address`, `status: OrderStatus`, `paymentStatus: PaymentStatus`, `correlationId: UUID`, `version: integer`, `createdAt: timestamp`, `updatedAt: timestamp`. Payment status is an eventually consistent projection of Payments data. |
| Payment | `id: UUID`, `orderId: UUID`, `total: Money`, `status: PaymentStatus`, `providerReference: string?`, `failureCode: string?`, `updatedAt: timestamp`. Unique orderId prevents more than one logical charge per order. Never store raw card data. |
| RestaurantOrder | `orderId: UUID`, `restaurantId: UUID`, `items: OrderItem[]`, `status: RestaurantOrderStatus`, `reason: string?`, `version: integer`. Reason is mandatory on rejection. |
| Driver | `id: UUID`, `profileId: UUID`, `available: boolean`. At most one active assignment per driver in this draft. |
| Delivery | `id: UUID`, `orderId: UUID`, `restaurantId: UUID`, `driverId: UUID?`, `deliveryAddress: Address`, `status: DeliveryStatus`, `version: integer`. A driver is required from ASSIGNED onward. |

Only Identity stores login credentials through the selected identity provider. Clients cannot submit roles, customerId, prices, order totals or global order status when creating an order. Subject identity comes from the verified token.

### 2.3 Quote integrity

Catalog creates quotes using current PostgreSQL values, not an unchecked Redis price. It signs `iss`, `aud`, `exp`, `jti`, `customerId`, `restaurantId`, `items` and `total` with an asymmetric key. The issuer is Catalog, the audience is Orders, jti is the quote ID, and expiry is 120 seconds after creation. Orders uses a pinned algorithm and locally available public keys; unknown keys fail closed. Tokens contain no address or payment secret and must not be logged.

Orders verifies signature, expiry, customer binding and totals before persisting a new order. A unique constraint on quote ID permits one order per quote. A valid quote commits the displayed price until expiry even if the catalog subsequently changes. Restaurant Operations still decides whether it can fulfill the order. Expired quotes require a new checkout quote, except an already accepted idempotent replay.

### 2.4 Order state rules

| Current state | Incoming event and guard | Next state / action |
| --- | --- | --- |
| None | Valid HTTP creation, order + outbox committed | CREATED; publish OrderCreated. |
| CREATED | OrderCreated | Request ChargePayment once. |
| CREATED | PaymentSucceeded for matching order and amount | Keep CREATED, paymentStatus SUCCEEDED; request restaurant acceptance once. |
| CREATED | PaymentFailed, definitive failure only | PAYMENT_FAILED; no restaurant request. |
| CREATED | RestaurantAccepted, payment SUCCEEDED | ACCEPTED. |
| CREATED | RestaurantRejected, payment SUCCEEDED | REJECTED; request RefundPayment once; paymentStatus REFUND_PENDING. |
| ACCEPTED | RestaurantPreparing | PREPARING; request delivery assignment once. |
| PREPARING | DeliveryAssigned | Keep PREPARING; store assignment reference. |
| PREPARING | DeliveryPickedUp for the assigned driver | ON_THE_WAY. |
| ON_THE_WAY | DeliveryCompleted | DELIVERED. |
| REJECTED | PaymentRefunded | Keep REJECTED; paymentStatus REFUNDED. |

Only initial creation is HTTP-driven. All subsequent global order transitions result from consumed events. Terminal order states never reopen. A rejected order may still have a pending financial compensation. Assignment alone does not imply pickup. Duplicate events have no effect, and an early or stale event cannot force a transition or regress state. A provider timeout leaves the order CREATED with paymentStatus UNKNOWN via PaymentPendingReconciliation; it must not emit PaymentFailed.

## 3. REST APIs

### 3.1 Shared HTTP contract

- Base path: `/api/v1`; public traffic uses HTTPS. JSON request and response bodies use `application/json`.
- Authentication: `Authorization: Bearer <access-token>`. Gateway and services verify signature, issuer, audience and expiry. Roles do not bypass ownership checks. Service-to-service calls use service credentials, not client-supplied identity headers.
- Login uses the identity provider's standard OIDC authorization-code flow with PKCE and discovery; credentials are not accepted by a custom EatsNow login endpoint. Restaurant/driver onboarding is administrative in this draft.
- `X-Correlation-ID`: optional UUID on requests, generated when absent or invalid and returned on every response. W3C `traceparent` is propagated when valid. Each order stores its original workflow correlationId, including on later action messages.
- Shared errors apply to **every endpoint**: `400 INVALID_REQUEST` for malformed input, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `429 RATE_LIMITED` with Retry-After, `500 INTERNAL_ERROR` for unexpected faults and `503 SERVICE_UNAVAILABLE` when the serving service cannot safely complete the operation. A downstream asynchronous outage alone does not justify a 503 on order creation.
- Object access is restricted to its customer, owning restaurant or assigned driver as specified below. Inaccessible or absent object IDs return `404 NOT_FOUND` to prevent enumeration. Use 403 for an authenticated role that cannot call the endpoint at all.
- GET requests have no body. Lists use `limit` (default 20, maximum 100) and optional opaque `cursor`; responses are `{ "items": [...], "nextCursor": string | null }`.
- All mutating endpoints below require `Idempotency-Key: UUID`. Scope is authenticated subject + method + concrete path + key. Store a canonical request hash and original successful response atomically with the change. Same key and body replays the original response; changed body returns `409 IDEMPOTENCY_CONFLICT`. Serialize concurrent attempts; a still-running attempt returns `409 REQUEST_IN_PROGRESS` with Retry-After. Validation/authentication failures are not cached. Retain keys for at least 7 days; quote/order uniqueness remains permanent.
- Successful responses specify their schema below; endpoint-specific errors supplement the shared errors, including the shared 5xx responses. Every error uses Error. Example: `{"code":"INVALID_REQUEST","message":"quantity must be between 1 and 20","correlationId":"00000000-0000-4000-8000-000000000040"}`.

Example authenticated read:

```http
GET /api/v1/profiles/me HTTP/1.1
Authorization: Bearer <access-token>
X-Correlation-ID: 00000000-0000-4000-8000-000000000040
```

### 3.2 Profiles

| Method and path | Authentication / authorization | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| GET /profiles/me | Any active role; own profile | None | 200 Profile; shared 4xx/5xx | Response: `{"id":"00000000-0000-4000-8000-000000000001","displayName":"Alex","email":"alex@example.test","roles":["USER"],"active":true}`. |
| PATCH /profiles/me | Any active role; own profile | `{displayName: string[1..100]}` only | 200 Profile; shared 4xx/5xx; 409 idempotency conflicts | Request: `{"displayName":"Alex Rivera"}`. Response: same Profile with displayName changed. Roles, email and ownership links cannot be changed here. |

### 3.3 Catalog and quotes

| Method and path | Authentication / authorization | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| GET /restaurants | Any active role | None; pagination query | 200 paginated Restaurant; shared 4xx/5xx | Response: `{"items":[{"id":"00000000-0000-4000-8000-000000000002","name":"Burger House","available":true}],"nextCursor":null}`. |
| GET /restaurants/{restaurantId}/menu | Any active role | None; pagination query | 200 paginated MenuItem; 404 restaurant not found; shared 4xx/5xx | Response: `{"items":[{"id":"00000000-0000-4000-8000-000000000003","restaurantId":"00000000-0000-4000-8000-000000000002","name":"Classic Burger","price":{"amountMinor":2500000,"currency":"COP"},"available":true,"version":1}],"nextCursor":null}`. |
| POST /quotes | USER | `{restaurantId: UUID, items: RequestedItem[1..50]}` | 201 Quote; 404 restaurant/item not found; 409 ITEM_UNAVAILABLE or idempotency conflict; shared 4xx/5xx | Request: `{"restaurantId":"00000000-0000-4000-8000-000000000002","items":[{"menuItemId":"00000000-0000-4000-8000-000000000003","quantity":1}]}`. Response: Quote with one Classic Burger, total `{"amountMinor":2500000,"currency":"COP"}`, expiry in 120 seconds and quoteToken `<signed-quote>`. |

Catalog list/menu keys expire after 60 seconds. Catalog-owned menu writes invalidate the relevant keys after commit; initial menus can be loaded administratively for Wave 2. If invalidation fails, TTL bounds staleness. Redis failure falls back to Catalog PostgreSQL with bounded concurrency, never to Orders tables. If neither source is available, return 503. No dependency on Payments, Restaurant Operations or Delivery is allowed on this read path.

### 3.4 Orders and payments

| Method and path | Authentication / authorization | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| POST /orders | USER; quote customer must match token subject | `{quoteToken: string, deliveryAddress: Address, paymentMethodToken: string[1..2048]}` | 202 OrderReceipt; 409 QUOTE_ALREADY_USED or idempotency conflict; 422 INVALID_QUOTE / QUOTE_EXPIRED; shared 4xx/5xx | Full example below. |
| GET /orders/{orderId} | USER owner | None | 200 Order; 404; shared 4xx/5xx | Response: order for Classic Burger, total `2500000` COP minor units, status `PREPARING`, paymentStatus `SUCCEEDED`; all fields follow Order. |
| GET /orders/{orderId}/payment | USER owner; ownership verified through trusted authorization, not caller-supplied customerId | None | 200 PaymentSummary; 404; shared 4xx/5xx | Response: `{"orderId":"00000000-0000-4000-8000-000000000010","status":"UNKNOWN","total":{"amountMinor":2500000,"currency":"COP"},"updatedAt":"2026-10-13T15:00:05Z"}`. |

OrderReceipt is `{orderId: UUID, status: "CREATED", correlationId: UUID}`. PaymentSummary is `{orderId: UUID, status: PaymentStatus, total: Money, updatedAt: timestamp}`. The payment route returns the Orders payment projection; immediately after creation it returns PENDING, not 404 because a payment record has not arrived. Provider references and method tokens are not exposed. Payments remains the financial source of truth.

```http
POST /api/v1/orders HTTP/1.1
Authorization: Bearer <user-access-token>
Content-Type: application/json
Idempotency-Key: 00000000-0000-4000-8000-000000000030
X-Correlation-ID: 00000000-0000-4000-8000-000000000040

{
  "quoteToken": "<signed-quote>",
  "deliveryAddress": {"line1": "123 Example Street", "city": "Medellin"},
  "paymentMethodToken": "<opaque-provider-token>"
}
```

```http
HTTP/1.1 202 Accepted
Content-Type: application/json
Location: /api/v1/orders/00000000-0000-4000-8000-000000000010
X-Correlation-ID: 00000000-0000-4000-8000-000000000040

{
  "orderId": "00000000-0000-4000-8000-000000000010",
  "status": "CREATED",
  "correlationId": "00000000-0000-4000-8000-000000000040"
}
```

Return 202 only after the order, idempotency result and OrderCreated outbox entry commit. Never wait for broker delivery or a downstream business response. If the database cannot commit, return 503; on an uncertain commit outcome the client retries the same key. Encrypt the payment method token at rest, restrict message access and remove it from transient order storage once Payments has durably accepted the command. It is never included in public Order responses or logs.

### 3.5 Restaurant operations

Restaurant Operations owns the restaurant inbox (RestaurantOrder). A RestaurantOrder is created with status `PENDING` only when Restaurant Operations consumes the restaurant acceptance request that Orders sends after `PaymentSucceeded` (Section 2.4), so restaurants never see unpaid orders. Its identifier is the global `orderId`.
 
**Authorization.** All endpoints require the `RESTAURANT` role; any other role receives `403 FORBIDDEN` (Feature 1). The caller's `restaurantId` is taken from the verified token/profile, never from the path, query or body. A RestaurantOrder that belongs to another restaurant, or does not exist, returns `404 NOT_FOUND` to prevent enumeration.
 
**Allowed local transitions.**
 
| Current status | Action | Next status | Event published (via outbox) |
| --- | --- | --- | --- |
| PENDING | accept | ACCEPTED | RestaurantAccepted |
| PENDING | reject | REJECTED | RestaurantRejected |
| ACCEPTED | start preparation | PREPARING | RestaurantPreparing |
 
Any other combination returns `409 INVALID_TRANSITION`. REJECTED and PREPARING are final for this service in this draft (pickup belongs to Delivery).
 
| Method and path | Authentication / authorization | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| GET /restaurant-orders | RESTAURANT; only orders of the caller's restaurant | None; pagination query plus optional `status: RestaurantOrderStatus` filter | 200 paginated RestaurantOrder; shared 4xx/5xx | `GET /restaurant-orders?status=PENDING&limit=20`. Response: `{"items":[{"orderId":"00000000-0000-4000-8000-000000000010","restaurantId":"00000000-0000-4000-8000-000000000002","items":[{"menuItemId":"00000000-0000-4000-8000-000000000003","name":"Classic Burger","quantity":1,"unitPrice":{"amountMinor":2500000,"currency":"COP"}}],"status":"PENDING","reason":null,"version":1}],"nextCursor":null}`. |
| GET /restaurant-orders/{orderId} | RESTAURANT owner | None | 200 RestaurantOrder; 404 NOT_FOUND; shared 4xx/5xx | Response: the RestaurantOrder shown above. |
| POST /restaurant-orders/{orderId}/accept | RESTAURANT owner; requires Idempotency-Key | `{expectedVersion: integer}` | 200 RestaurantOrder with status ACCEPTED; 404 NOT_FOUND; 409 INVALID_TRANSITION, VERSION_CONFLICT or idempotency conflict; shared 4xx/5xx | Request: `{"expectedVersion":1}`. Response: RestaurantOrder with `"status":"ACCEPTED","version":2`. |
| POST /restaurant-orders/{orderId}/reject | RESTAURANT owner; requires Idempotency-Key | `{expectedVersion: integer, reason: string[1..300]}` | 200 RestaurantOrder with status REJECTED; 404 NOT_FOUND; 409 INVALID_TRANSITION, VERSION_CONFLICT or idempotency conflict; shared 4xx/5xx (400 INVALID_REQUEST if reason is missing or empty) | Request: `{"expectedVersion":1,"reason":"Kitchen closed due to a gas outage"}`. Response: RestaurantOrder with `"status":"REJECTED","reason":"Kitchen closed due to a gas outage","version":2`. |
| POST /restaurant-orders/{orderId}/preparation | RESTAURANT owner; requires Idempotency-Key | `{expectedVersion: integer}` | 200 RestaurantOrder with status PREPARING; 404 NOT_FOUND; 409 INVALID_TRANSITION, VERSION_CONFLICT or idempotency conflict; shared 4xx/5xx | Request: `{"expectedVersion":2}`. Response: RestaurantOrder with `"status":"PREPARING","version":3`. |
 
Endpoint-specific errors:
 
| Code | HTTP status | When |
| --- | --- | --- |
| INVALID_TRANSITION | 409 | The action is not allowed from the current status (for example, accepting an order that is already REJECTED). `details` contains `currentStatus`. |
| VERSION_CONFLICT | 409 | `expectedVersion` does not match the stored version (optimistic concurrency). `details` contains `currentVersion`; the client must reload the order before retrying. |
 
Example decision request and response:
 
```http
POST /api/v1/restaurant-orders/00000000-0000-4000-8000-000000000010/reject HTTP/1.1
Authorization: Bearer <restaurant-access-token>
Content-Type: application/json
Idempotency-Key: 00000000-0000-4000-8000-000000000060
X-Correlation-ID: 00000000-0000-4000-8000-000000000061
 
{
  "expectedVersion": 1,
  "reason": "Kitchen closed due to a gas outage"
}
```
 
```http
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Correlation-ID: 00000000-0000-4000-8000-000000000061
 
{
  "code": "VERSION_CONFLICT",
  "message": "The restaurant order was modified by another request",
  "correlationId": "00000000-0000-4000-8000-000000000061",
  "details": {"currentVersion": 2}
}
```
 
**Concurrency.** Two staff members may act on the same order at the same time. Each decision is a conditional update (`WHERE orderId = ? AND version = expectedVersion`); only one succeeds and the other receives `409 VERSION_CONFLICT`. This guarantees at most one restaurant decision per order. Retrying the same request with the same Idempotency-Key replays the original 200 response instead of failing.
 
**Atomic commit and asynchronous propagation.** Each successful action commits, in a single Restaurant Operations database transaction: the new RestaurantOrder status and version, the idempotency record and the outbox entry for the corresponding event. The 200 response confirms only the local decision. Restaurant Operations never calls Orders synchronously and never writes Orders tables; the global order status changes later when Orders consumes the event (Section 2.4). The event uses the stored workflow `correlationId` from the original order (Section 4.4), `causationId: null` because it is HTTP-triggered, `producer: "restaurants"`, `aggregateId` equal to `orderId`, and `aggregateVersion` equal to the new RestaurantOrder version. Payload details are defined in Section 4.2.
 
**Alignment with requirements.** A USER calling any decision endpoint receives 403 and no event is created (Feature 1, "Reject an unauthorized restaurant action"). A restaurant requesting another restaurant's order receives 404 (Feature 1, "Protect another restaurant's order"). Rejecting a paid order publishes RestaurantRejected, which leads Orders to REJECTED and one RefundPayment (Feature 4, "Refund a rejected paid order"). Preparation can only start after acceptance in this service, and Orders still waits durably if RestaurantPreparing arrives before RestaurantAccepted (Feature 4, "Handle preparation before acceptance").

### 3.6 Drivers and deliveries

**TODO - Team contribution required.** Define the contracts for driver availability, assigned deliveries, pickup confirmation and delivery completion.

- [ ] For each endpoint, specify method, path, authentication, assigned-driver ownership checks, request body, responses with 2xx/4xx/5xx status codes and a concrete example.
- [ ] Define response schemas and concurrency rules using Driver, Delivery and the shared HTTP contract.
- [ ] Specify how assignment prevents multiple active deliveries per driver, how unavailable drivers are handled and when a driver becomes available again.
- [ ] Map pickup and completion actions to asynchronous events without treating assignment alone as ON_THE_WAY.

## 4. Asynchronous Events and Integration Contracts

### 4.1 Envelope and transport

The proposed common envelope below is a starting point for the pending message catalog. Completed contracts must list every required payload field and refer to the common types in Section 2. The example illustrates one event, not the complete messaging specification.

```json
{
  "messageId": "00000000-0000-4000-8000-000000000050",
  "messageType": "RestaurantAccepted",
  "schemaVersion": 1,
  "occurredAt": "2026-10-13T15:01:00Z",
  "producer": "restaurants",
  "orderId": "00000000-0000-4000-8000-000000000010",
  "correlationId": "00000000-0000-4000-8000-000000000040",
  "causationId": "00000000-0000-4000-8000-000000000051",
  "aggregateId": "00000000-0000-4000-8000-000000000010",
  "aggregateVersion": 2,
  "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
  "payload": {
    "restaurantId": "00000000-0000-4000-8000-000000000002"
  }
}
```

All envelope fields are required except causationId, which may be null for a root operation. messageType must match a name in the completed Section 4.2 catalog. producer is `orders`, `payments`, `restaurants` or `delivery`. aggregateId identifies the producer-owned aggregate, aggregateVersion is its monotonically increasing local version, and orderId links the business workflow. A consumer must not compare versions from different producers as a global sequence. tracestate may optionally accompany traceparent as a string. Commands reference the requesting aggregate; they do not dictate the receiving aggregate's version.

The draft proposes durable RabbitMQ topic exchanges `eatsnow.events.v1` and `eatsnow.commands.v1`, with the case-sensitive messageType as routing key. Events describe completed facts; commands request work from one owner. Queue names and bindings remain to be defined in Section 4.2. Completed contracts must state how they apply the delivery policy in Section 4.3, including persistence, publisher confirms, manual acknowledgements, duplicate handling, ordering and retries.

### 4.2 Message catalog

**TODO - Team contribution required.** Build the message catalog from the proposed state rules in Section 2.4 and the acceptance scenarios. The common envelope is provided in Section 4.1; business payloads and routing contracts are intentionally pending.

- [ ] Inventory commands and events for order creation, payment results, restaurant decisions/preparation, delivery assignment/pickup/completion and refunds.
- [ ] For each message, define its name, kind, producer, consumers, queue or topic, routing key, payload schema and example.
- [ ] State delivery guarantees for every contract: ordering, idempotency and retries, referencing Section 4.3 and documenting any differences.
- [ ] Define required field constraints, schema versions and validation against the expected order, amount, restaurant and driver.
- [ ] Define handling and access restrictions for payment tokens and delivery addresses; do not expose them in ordinary logs.
- [ ] Reconcile final names and financial semantics with the ADRs and every state rule/scenario that references them.

Suggested table to complete:

| Name / kind | Producer | Consumers | Queue or topic / routing key | Payload schema and example | Ordering / idempotency / retries |
| --- | --- | --- | --- | --- | --- |
| TODO | TODO | TODO | TODO | TODO | TODO |

### 4.3 Delivery guarantees, ordering and failures

1. **At least once:** persist business changes and outgoing messages in one database transaction. An outbox relay retries unconfirmed publication after 1, 5, 30 and then every 60 seconds until confirmed. Retain unconfirmed entries; alert on the oldest pending entry exceeding 60 seconds. A broker outage must not erase accepted orders.
2. **Idempotency:** each consumer atomically stores `(consumer, messageId)` with its business effects and outgoing messages. Acknowledge only after commit. Deduplication records live at least as long as their business aggregates and permitted replay history. Business constraints additionally prevent multiple payments, restaurant decisions or deliveries from semantically duplicate commands carrying new IDs.
3. **Ordering:** no global order or exactly-once claim is made. Serialize state writes per aggregate with a lock or optimistic version check. Store valid but premature messages durably as pending, acknowledge only after that storage commits, and reevaluate after each relevant transition and every 10 seconds. For example, RestaurantPreparing waits for RestaurantAccepted, and DeliveryPickedUp waits for DeliveryAssigned. Do not blindly discard lower-version messages whose prerequisites are still missing. Contradictory transitions are quarantined; pending messages older than 5 minutes raise an alert without silently changing state.
4. **Transport retries:** transient handler failures retry after 5, 30 and 120 seconds; after the third failed retry send to `<queue>.dlq` via the durable `eatsnow.dlx.v1` exchange, using the source queue name as routing key. Unknown schema versions and malformed messages go directly to that queue's DLQ. Redrive after repair preserves messageId, correlationId and causationId. Normal pending business work is not a transport failure.
5. **External payments:** payment API calls cannot be part of a database transaction. Persist the attempt before calling the adapter and always reuse the charge/refund idempotency key across crashes and retries. The adapter must support idempotent operations and status lookup by that key. On a 5-second timeout, persist an unknown outcome and reconcile every 30 seconds; never start a new charge with a new key. Alert after 5 minutes without a definitive outcome, but retain the pending attempt for recovery. Emit PaymentSucceeded, PaymentFailed or PaymentRefunded only after the definitive outcome is durably recorded with an outbox entry.
6. **Refund failures:** the order stays REJECTED and the payment stays REFUND_PENDING until refund confirmation. A refund timeout emits PaymentPendingReconciliation with operation REFUND and must not regress payment status to UNKNOWN for the original charge. Permanent provider refusal raises an operational alert and requires reconciliation; never claim the customer was refunded without confirmation.
7. **Evolution:** schemaVersion 1 consumers tolerate additive optional payload fields, validate required fields and reject incompatible changes. Breaking changes require a new schema version and explicit consumer migration.

### 4.4 Workflow correlation

The HTTP order request creates the workflow correlationId; Orders preserves it in every command. Each receiving service stores it alongside the local order reference. Later restaurant and driver actions use this stored ID for workflow messages, even when their HTTP request has a different correlation header. Log both requestCorrelationId and correlationId when they differ. causationId references the triggering message, or null for an HTTP-triggered action. A publication span injects traceparent; consumers extract it and create processing spans. Separate user actions start their own traces and link to stored workflow trace context; a correlation ID alone does not replace distributed tracing.

## Requirements

This section fulfills the assignment's requirements and WHEN/THEN scenarios for the five mandatory features. Each normative requirement uses SHALL and has named scenarios. The scenarios are acceptance contracts for Wave 2, not evidence of an existing implementation. Performance scenarios use a documented local environment, dataset and load generator so results can be reproduced.

### Requirement: Feature 1 - Centralized profile and access management

The system SHALL centrally manage USER, RESTAURANT and DRIVER profiles and access, authenticate requests, enforce role and resource ownership restrictions, and prevent unauthorized role changes.

#### Scenario: Read the authenticated user's profile

- **WHEN** an authenticated USER requests GET /profiles/me
- **THEN** Identity returns that user's profile and roles without permitting client-side role changes.

#### Scenario: Reject an unauthorized restaurant action

- **WHEN** a USER calls a restaurant decision endpoint
- **THEN** the system returns 403 and creates no decision or event.

#### Scenario: Protect another restaurant's order

- **WHEN** a RESTAURANT principal requests an order owned by another restaurant
- **THEN** the system returns 404 and discloses no order data.

#### Scenario: Reject an invalid access token

- **WHEN** an access token is missing, expired or invalid
- **THEN** the Gateway rejects the request with 401 before business processing.

### Requirement: Feature 2 - Independent, responsive restaurant catalog

The system SHALL serve restaurant and menu queries independently of purchasing workflows, preserve catalog response contracts during supported cache failures, and provide bounded price staleness and reproducible read-performance acceptance criteria.

#### Scenario: Read the catalog during a downstream outage

- **WHEN** catalog entries are cached and Payments or Restaurant Operations is unavailable for 30 seconds
- **THEN** restaurant/menu reads continue returning 200 without calling those services or the Orders database.

#### Scenario: Fall back when Redis is unavailable

- **WHEN** Redis is unavailable but Catalog PostgreSQL is healthy
- **THEN** catalog reads fall back to PostgreSQL with bounded concurrency and preserve the response schema.

#### Scenario: Preserve agreed prices after a catalog update

- **WHEN** a catalog price changes
- **THEN** cache invalidation or the 60-second TTL removes stale listings, new quotes use the current authoritative price, and previously valid quotes retain their agreed price until expiry.

#### Scenario: Read a warmed catalog during concurrent purchasing

- **WHEN** 50 concurrent clients read a warmed catalog while 10 orders per second are submitted for 60 seconds
- **THEN** target catalog p95 latency is at most 300 ms with less than 1% server errors; compare with the same read-only baseline and report both results.

### Requirement: Feature 3 - Event-driven order creation

The system SHALL acknowledge valid orders after durable persistence without awaiting downstream processing, retain accepted work during broker outages, prevent duplicate logical orders and charges, and reconcile ambiguous payment outcomes.

#### Scenario: Create an order while Restaurant Operations is unavailable

- **WHEN** a USER submits a valid quote and payment token while Restaurant Operations is unavailable for 30 seconds
- **THEN** Orders commits the order/outbox and returns 202 with orderId, Location and correlationId without awaiting restaurant or payment processing.

#### Scenario: Recover accepted orders after a broker outage

- **WHEN** RabbitMQ is unavailable but the Orders database is healthy
- **THEN** order creation still returns 202 after commit, the outbox remains pending, and processing resumes after broker recovery without a second logical order or charge.

#### Scenario: Handle concurrent idempotent order requests

- **WHEN** two concurrent requests use the same subject, path, Idempotency-Key and body
- **THEN** at most one order is created; a retry receives the original 202 response or temporarily 409 REQUEST_IN_PROGRESS.

#### Scenario: Reconcile a lost payment response

- **WHEN** the provider charges successfully but its response is lost
- **THEN** Payments records UNKNOWN, reconciles using the same provider key, eventually records SUCCEEDED and never issues a second logical charge.

#### Scenario: Accept orders under load with downstream services stopped

- **WHEN** 10 valid order requests per second run for 60 seconds with healthy local persistence and downstream services stopped
- **THEN** target order-creation p95 latency is at most 500 ms and no accepted order lacks a durable outbox entry.

### Requirement: Feature 4 - Order transitions driven by asynchronous events

The system SHALL transition persisted orders through CREATED, ACCEPTED, PREPARING and ON_THE_WAY using asynchronous events and valid prerequisites, without skipping or regressing states. It SHALL handle delivery completion, duplicate or reordered events, paid-order rejection and pending driver assignment as specified below.

#### Scenario: Progress through the mandatory order states

- **WHEN** PaymentSucceeded, RestaurantAccepted, RestaurantPreparing, DeliveryAssigned and DeliveryPickedUp are processed with matching identities and valid prerequisites
- **THEN** the order progresses CREATED -> ACCEPTED -> PREPARING -> ON_THE_WAY; assignment alone leaves it PREPARING.

#### Scenario: Complete a delivery

- **WHEN** a driver completes a picked-up delivery
- **THEN** DeliveryCompleted eventually changes the order from ON_THE_WAY to DELIVERED and releases the driver.

#### Scenario: Handle preparation before acceptance

- **WHEN** RestaurantPreparing arrives before RestaurantAccepted
- **THEN** it waits durably for the missing prerequisite instead of skipping ACCEPTED, being lost or regressing the order later.

#### Scenario: Consume a duplicate event

- **WHEN** the same event is delivered twice
- **THEN** the second delivery causes no additional state change, command, charge or refund.

#### Scenario: Refund a rejected paid order

- **WHEN** a restaurant rejects a paid order
- **THEN** the order becomes REJECTED, one logical refund is requested and payment remains REFUND_PENDING until PaymentRefunded confirms REFUNDED.

#### Scenario: Wait for an available driver

- **WHEN** no driver is available
- **THEN** the order remains PREPARING and assignment remains pending without reporting the order as ON_THE_WAY or failing its original HTTP creation request.

### Requirement: Feature 5 - Correlated logs, metrics and distributed traces

The system SHALL expose correlated logs and distributed traces across the order lifecycle, distinguish definitive and ambiguous financial outcomes during investigation, and alert operators about failed messages and stalled publication without exposing sensitive data.

#### Scenario: Trace an order across participating services

- **WHEN** an order is created and processed through Payments, Restaurant Operations and Delivery
- **THEN** structured logs from every participating service contain the same orderId and workflow correlationId, and Tempo exposes HTTP, publication and consumption spans linked through propagated trace context.

#### Scenario: Investigate an ambiguous payment outcome

- **WHEN** an operator investigates a payment timeout
- **THEN** Grafana allows searching Loki by orderId/correlationId and following traceId into Tempo; logs distinguish UNKNOWN, SUCCEEDED, FAILED and REFUNDED without treating an authorization or timeout as proof of capture or refund.

#### Scenario: Detect failed or stalled message processing

- **WHEN** a message reaches a DLQ or an outbox entry stays pending over 60 seconds
- **THEN** an operational alert identifies the affected service/queue and investigation can recover the order's correlation context.

## 6. Observability and Verification Contract

**TODO - Team contribution required.** Complete the observable contracts and verification plan for Feature 5. For Case 6, distributed tracing with OpenTelemetry and Jaeger or Tempo is mandatory, alongside Prometheus, Loki and Grafana. Tempo remains the proposed choice in this draft; the pending work is to specify the details, not to decide whether tracing is needed.

- [ ] Define the structured log schema and an example, including orderId, correlationId, traceId, service, operation and outcome; specify which fields are required for each operation.
- [ ] Specify HTTP and message trace propagation using Section 4.4, including later restaurant/driver actions, sampling and links between traces.
- [ ] Define metrics, labels, collection endpoints, dashboards and alert conditions for failures, message backlog and payment reconciliation. Avoid per-order metric labels.
- [ ] Specify sensitive-data exclusions and behavior when telemetry export fails, without blocking order processing.
- [ ] Describe how an operator investigates an order and determines whether payment succeeded, failed, remains unknown or was refunded.
- [ ] Define reproducible verification evidence: tested commit, Docker Compose configuration, environment, dataset, load, latency percentiles and error rates. Use synthetic payment tokens and no real charges.
- [ ] Check every mandatory feature against its scenarios and resolve all TODOs before final submission; no implementation or measured performance results exist yet.
