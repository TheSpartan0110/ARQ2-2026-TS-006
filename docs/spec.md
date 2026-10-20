# EatsNow Contract Specification

Status: Complete draft - pending team review | Version: 1.0.0 | Team: TS-006

## Purpose

Define the observable contracts and acceptance requirements for EatsNow, ensuring independent catalog access, asynchronous order processing, valid order transitions and end-to-end traceability.

This single-file academic deliverable remains at `docs/spec.md` as required by the assignment. Section 5 uses OpenSpec requirement and scenario syntax; the supporting sections organize the required domain, REST and messaging contracts. This document is a baseline specification, not an OpenSpec change delta or an initialized CLI workspace.

Container names, technologies and integration paths follow the official C4 Level 2 container diagram (`docs/assets/Modeloc4gp6.drawio.png`). Where the assignment requires something the diagram does not yet show (the events needed for the mandatory order states, Prometheus metrics, Tempo traces and the Notification Service), this specification adds it and the diagram must be updated to match.

## 1. Overview

This specification defines the contracts for Case 6: EatsNow - Delivery and Order Orchestration. Wave 2 implementation must conform to the approved version of this file. The Tech Spec remains in Google Docs; this file contains contracts, not a copy of that document.

The system must accept an order without waiting for Payments, Restaurants or Delivery to finish processing. A restaurant outage must not turn an already persisted order into an HTTP 500 response. Operators must be able to determine the order and payment outcomes through correlated logs and distributed traces.

### 1.1 Services and ownership

| Container (short name) | Responsibility and owned data |
| --- | --- |
| API Gateway (Spring Cloud Gateway) | Single public HTTP entry point for the Customer, Restaurant and Delivery Driver apps: token validation, routing, request limits and correlation headers. No business data ownership. |
| Identity & Access Service ("Identity") | Central user profiles, authentication, roles and links to restaurant or driver identities. Schema `identity`. |
| Catalog Service ("Catalog") | Restaurant listings, menus, prices, availability and immutable checkout quotes. Schema `catalog` is the source of truth; Redis is a cache-aside read accelerator. |
| Order Service ("Orders") | Orders, purchased item snapshots, global order states, orchestration progress, idempotency records and outbox. It creates orders and coordinates states by consuming events; no separate orchestrator container exists. Schema `orders`. |
| Payment Service ("Payments") | Payment attempts, provider references, payment status, reconciliation and refunds. Calls the External Payment Provider over HTTPS. Schema `payments`. |
| Restaurant Service ("Restaurants") | Restaurant order inbox, acceptance/rejection and preparation progress. This is separate from Catalog's commercial model. Schema `restaurants`. |
| Delivery Service ("Delivery") | Driver duty status, assignments, pickup and delivery progress. Schema `deliveries`. |
| Notification Service ("Notifications") | Asynchronous customer notifications consumed from order status events. It never blocks order creation or state transitions. Schema `notifications` (deduplication and send attempts only). |
| External Payment Provider | Third-party HTTPS payment API. Wave 2 uses a local test double. |
| Observability | OpenTelemetry SDKs and Collector, Prometheus metrics, Grafana Loki logs, Grafana Tempo traces and Grafana dashboards. |

Each business service owns its persistent data. As in the container diagram, a single shared PostgreSQL instance hosts one schema per service, and each service connects with its own database user that has privileges only on its own schema. No service reads or writes another service's schema. Catalog uses an independent connection pool so purchasing load cannot exhaust catalog reads. RabbitMQ transports durable messages. Services and infrastructure run through Docker Compose in Wave 2. Payment and email providers may use local test doubles; real credentials or card numbers must never be committed.

### 1.2 Design decisions

The ADRs in `docs/adrs/` establish RabbitMQ with Transactional Outbox and idempotent consumers (ADR 001), Redis cache-aside for the catalog (ADR 002), OpenTelemetry with Prometheus, Loki, Tempo and Grafana (ADR 003), PostgreSQL with one schema per service (ADR 004), Spring Authorization Server for identity and access (ADR 005) and an asynchronous Notification Service (ADR 006). This specification makes them implementable with the following decisions:

- Identity & Access embeds an OIDC authorization server and issues JWT access tokens (ADR 005). Customers self-register as USER; RESTAURANT, DRIVER and ADMIN accounts are created only by an ADMIN (Section 3.2). Public role escalation is forbidden.
- Customers obtain a signed, short-lived quote before order submission. Orders verifies it locally, avoiding a synchronous Catalog call during order creation.
- Payment completes before restaurant acceptance is requested, so restaurants never see unpaid orders. If the restaurant rejects a paid order, it is refunded asynchronously.
- `PaymentAuthorized` (the event name in the container diagram) is emitted only when the provider confirms the charge. The provider adapter authorizes and captures in one idempotent operation; a pending, timed-out or merely reserved authorization never produces `PaymentAuthorized`.
- `DELIVERED`, `REJECTED` and `PAYMENT_FAILED` are added to the four mandatory order states. Customer cancellation, partial refunds, tips, taxes, delivery fees and multiple restaurants per order are out of scope.
- Grafana Tempo is the mandatory distributed tracing backend for Case 6.
- TTLs, timeouts and performance targets are acceptance criteria, not measured results.

## 2. Domain Schemas

### 2.1 Common types

All payloads use JSON with camelCase fields. Fields are required unless marked optional. IDs are UUID strings; timestamps are UTC RFC 3339 strings. Examples use valid UUIDs. Unknown request fields are rejected, except optional future fields on received events. Versions are positive integers.

| Type | Definition |
| --- | --- |
| Money | `{ "amountMinor": integer >= 0, "currency": "COP" }`. Values use minor units, never floating point. COP 25,000 is `2500000`. |
| Address | `{ "line1": string[1..200], "city": string[1..100], "instructions": string[0..300] (optional) }`. |
| Role | `USER`, `RESTAURANT`, `DRIVER`, `ADMIN`. |
| OrderStatus | `CREATED`, `ACCEPTED`, `PREPARING`, `ON_THE_WAY`, `DELIVERED`, `REJECTED`, `PAYMENT_FAILED`. |
| PaymentStatus | `PENDING`, `UNKNOWN`, `AUTHORIZED`, `FAILED`, `REFUND_PENDING`, `REFUNDED`. |
| RestaurantOrderStatus | `PENDING`, `ACCEPTED`, `REJECTED`, `PREPARING`. |
| DeliveryStatus | `UNASSIGNED`, `ASSIGNED`, `PICKED_UP`, `DELIVERED`. |
| Error | `{ "code": string, "message": string, "correlationId": UUID, "details": object (optional) }`. No internal stack traces or secrets. |

### 2.2 Business objects

| Schema | Fields and constraints |
| --- | --- |
| Profile | `id: UUID`, `displayName: string[1..100]`, `email: string(email)`, `roles: Role[1..4]`, `restaurantId: UUID?`, `driverId: UUID?`, `active: boolean`. A RESTAURANT role requires restaurantId; a DRIVER role requires driverId. |
| Restaurant | `id: UUID`, `name: string[1..120]`, `available: boolean`. |
| MenuItem | `id: UUID`, `restaurantId: UUID`, `name: string[1..120]`, `price: Money`, `available: boolean`, `version: integer`. |
| RequestedItem | `menuItemId: UUID`, `quantity: integer[1..20]`. Duplicate menuItemId values are invalid. |
| OrderItem | `menuItemId: UUID`, `name: string`, `quantity: integer[1..20]`, `unitPrice: Money`. Immutable snapshot from the quote. |
| Quote | `id: UUID`, `customerId: UUID`, `restaurantId: UUID`, `items: OrderItem[1..50]`, `total: Money`, `expiresAt: timestamp`, `quoteToken: string`. Total is the sum of quantity times unitPrice; all items share one restaurant and currency. |
| Order | `id: UUID`, `customerId: UUID`, `restaurantId: UUID`, `items: OrderItem[1..50]`, `total: Money`, `deliveryAddress: Address`, `status: OrderStatus`, `paymentStatus: PaymentStatus`, `correlationId: UUID`, `version: integer`, `createdAt: timestamp`, `updatedAt: timestamp`. Payment status is an eventually consistent projection of Payments data. |
| Payment | `id: UUID`, `orderId: UUID`, `total: Money`, `status: PaymentStatus`, `providerReference: string?`, `failureCode: string?`, `updatedAt: timestamp`. Unique orderId prevents more than one logical charge per order. Never store raw card data. |
| RestaurantOrder | `orderId: UUID`, `restaurantId: UUID`, `items: OrderItem[]`, `status: RestaurantOrderStatus`, `reason: string?`, `version: integer`. Reason is mandatory on rejection. |
| Driver | `id: UUID`, `profileId: UUID`, `onDuty: boolean`, `activeDeliveryId: UUID?`, `version: integer`. A driver is assignable only when onDuty is true and activeDeliveryId is null. At most one active delivery per driver. |
| Delivery | `id: UUID`, `orderId: UUID`, `restaurantId: UUID`, `driverId: UUID?`, `deliveryAddress: Address`, `status: DeliveryStatus`, `version: integer`, `createdAt: timestamp`, `updatedAt: timestamp`. A driver is required from ASSIGNED onward. One delivery per orderId (unique constraint). |
| Notification | `id: UUID`, `orderId: UUID`, `customerId: UUID`, `trigger: "ACCEPTED" \| "ON_THE_WAY" \| "DELIVERED" \| "REJECTED" \| "PAYMENT_FAILED" \| "REFUNDED"`, `channel: "EMAIL"`, `status: "PENDING" \| "SENT" \| "FAILED"`, `attempts: integer`, `updatedAt: timestamp`. Unique `(orderId, trigger)` sends at most one notification per trigger. Internal to Notifications; no public API. |

Only Identity stores login credentials through the selected identity provider. Clients cannot submit roles, customerId, prices, order totals or global order status when creating an order. Subject identity comes from the verified token.

### 2.3 Quote integrity

Catalog creates quotes using current PostgreSQL values, not an unchecked Redis price. It signs `iss`, `aud`, `exp`, `jti`, `customerId`, `restaurantId`, `items` and `total` with an asymmetric key. The issuer is Catalog, the audience is Orders, jti is the quote ID, and expiry is 120 seconds after creation. Orders uses a pinned algorithm and locally available public keys; unknown keys fail closed. Tokens contain no address or payment secret and must not be logged.

Orders verifies signature, expiry, customer binding and totals before persisting a new order. A unique constraint on quote ID permits one order per quote. A valid quote commits the displayed price until expiry even if the catalog subsequently changes. Restaurant Service still decides whether it can fulfill the order. Expired quotes require a new checkout quote, except an already accepted idempotent replay.

### 2.4 Order state rules

| Current state | Incoming event and guard | Next state / action |
| --- | --- | --- |
| None | Valid HTTP creation, order + outbox committed | CREATED; publish OrderCreated. |
| CREATED | OrderCreated | Request ChargePayment once. |
| CREATED | PaymentPendingReconciliation, operation CHARGE | Keep CREATED, paymentStatus UNKNOWN; no restaurant request yet. |
| CREATED | PaymentAuthorized for matching order and amount | Keep CREATED, paymentStatus AUTHORIZED; send RequestRestaurantAcceptance once. |
| CREATED | PaymentFailed, definitive failure only | PAYMENT_FAILED; no restaurant request. |
| CREATED | RestaurantAccepted, payment AUTHORIZED | ACCEPTED. |
| CREATED | RestaurantRejected, payment AUTHORIZED | REJECTED; send RefundPayment once; paymentStatus REFUND_PENDING. |
| ACCEPTED | RestaurantPreparing | PREPARING; send AssignDelivery once. |
| PREPARING | DeliveryAssigned | Keep PREPARING; store assignment reference. |
| PREPARING | DeliveryPickedUp for the assigned driver | ON_THE_WAY. |
| ON_THE_WAY | DeliveryCompleted | DELIVERED. |
| REJECTED | PaymentPendingReconciliation, operation REFUND | Keep REJECTED and paymentStatus REFUND_PENDING. |
| REJECTED | PaymentRefunded | Keep REJECTED; paymentStatus REFUNDED. |

Every change of `status` or `paymentStatus` is committed together with an `OrderStatusChanged` outbox entry, which is the only message Notifications consumes. Message names refer to the catalog in Section 4.2.

Only initial creation is HTTP-driven. All subsequent global order transitions result from consumed events. Terminal order states never reopen. A rejected order may still have a pending financial compensation. Assignment alone does not imply pickup. Duplicate events have no effect, and an early or stale event cannot force a transition or regress state. A provider timeout leaves the order CREATED with paymentStatus UNKNOWN via PaymentPendingReconciliation; it must not emit PaymentFailed.

## 3. REST APIs

### 3.1 Shared HTTP contract

- Base path: `/api/v1`; public traffic uses HTTPS. JSON request and response bodies use `application/json`.
- Authentication: `Authorization: Bearer <access-token>`. Gateway and services verify signature, issuer, audience and expiry. Roles do not bypass ownership checks. Service-to-service calls use service credentials, not client-supplied identity headers.
- Login uses the identity provider's standard OIDC authorization-code flow with PKCE and discovery; credentials are not accepted by a custom EatsNow login endpoint. Customers self-register through the Identity login pages and receive only the USER role.
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
| GET /admin/profiles | ADMIN | None; pagination query plus optional `role: Role` filter | 200 paginated Profile; shared 4xx/5xx | `GET /admin/profiles?role=DRIVER`. Response: `{"items":[{"id":"00000000-0000-4000-8000-000000000021","displayName":"Sam","email":"sam@example.test","roles":["DRIVER"],"driverId":"00000000-0000-4000-8000-000000000020","active":true}],"nextCursor":null}`. |
| POST /admin/profiles | ADMIN; requires Idempotency-Key | `{displayName: string[1..100], email: string(email), roles: Role[1..4], restaurantId: UUID?}` | 201 Profile; 409 EMAIL_ALREADY_REGISTERED or idempotency conflict; 422 INVALID_ROLE_LINK; shared 4xx/5xx | Request: `{"displayName":"Sam","email":"sam@example.test","roles":["DRIVER"]}`. Response: 201 Profile with a generated `driverId` and `active: true`. |
| PATCH /admin/profiles/{profileId} | ADMIN; requires Idempotency-Key | `{active: boolean}` | 200 Profile; 404 NOT_FOUND; 409 idempotency conflict; shared 4xx/5xx | Request: `{"active":false}`. Response: Profile with `"active":false`. |

Admin rules:

- RESTAURANT requires `restaurantId` (an existing Catalog restaurant); DRIVER causes Identity to generate a `driverId`. Any other combination, such as `restaurantId` without RESTAURANT, returns `422 INVALID_ROLE_LINK`.
- No password is sent through this API: the new user sets it on first login through the Identity activation flow.
- Deactivating a profile revokes its refresh tokens immediately; issued access tokens stay valid until their 15-minute expiry. Inactive profiles cannot log in.
- An ADMIN cannot deactivate their own profile (`409 INVALID_TRANSITION`).
- The `restaurantId` and `driverId` links become token claims that Restaurants and Delivery use for ownership checks. Delivery creates the Driver record with `onDuty: false` on the first authenticated request carrying a new `driverId`.

### 3.3 Catalog and quotes

| Method and path | Authentication / authorization | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| GET /restaurants | Any active role | None; pagination query | 200 paginated Restaurant; shared 4xx/5xx | Response: `{"items":[{"id":"00000000-0000-4000-8000-000000000002","name":"Burger House","available":true}],"nextCursor":null}`. |
| GET /restaurants/{restaurantId}/menu | Any active role | None; pagination query | 200 paginated MenuItem; 404 restaurant not found; shared 4xx/5xx | Response: `{"items":[{"id":"00000000-0000-4000-8000-000000000003","restaurantId":"00000000-0000-4000-8000-000000000002","name":"Classic Burger","price":{"amountMinor":2500000,"currency":"COP"},"available":true,"version":1}],"nextCursor":null}`. |
| POST /quotes | USER | `{restaurantId: UUID, items: RequestedItem[1..50]}` | 201 Quote; 404 restaurant/item not found; 409 ITEM_UNAVAILABLE or idempotency conflict; shared 4xx/5xx | Request: `{"restaurantId":"00000000-0000-4000-8000-000000000002","items":[{"menuItemId":"00000000-0000-4000-8000-000000000003","quantity":1}]}`. Response: Quote with one Classic Burger, total `{"amountMinor":2500000,"currency":"COP"}`, expiry in 120 seconds and quoteToken `<signed-quote>`. |

Catalog list/menu keys expire after 60 seconds. Catalog-owned menu writes invalidate the relevant keys after commit; initial menus can be loaded administratively for Wave 2. If invalidation fails, TTL bounds staleness. Redis failure falls back to Catalog PostgreSQL with bounded concurrency, never to Orders tables. If neither source is available, return 503. No dependency on Payments, Restaurant Service or Delivery is allowed on this read path.

### 3.4 Orders and payments

| Method and path | Authentication / authorization | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| POST /orders | USER; quote customer must match token subject | `{quoteToken: string, deliveryAddress: Address, paymentMethodToken: string[1..2048]}` | 202 OrderReceipt; 409 QUOTE_ALREADY_USED or idempotency conflict; 422 INVALID_QUOTE / QUOTE_EXPIRED; shared 4xx/5xx | Full example below. |
| GET /orders/{orderId} | USER owner | None | 200 Order; 404; shared 4xx/5xx | Response: order for Classic Burger, total `2500000` COP minor units, status `PREPARING`, paymentStatus `AUTHORIZED`; all fields follow Order. |
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

Restaurant Service owns the restaurant inbox (RestaurantOrder). A RestaurantOrder is created with status `PENDING` only when Restaurant Service consumes the `RequestRestaurantAcceptance` command that Orders sends after `PaymentAuthorized` (Section 2.4), so restaurants never see unpaid orders. Its identifier is the global `orderId`.
 
**Authorization.** All endpoints require the `RESTAURANT` role; any other role receives `403 FORBIDDEN` (Feature 1). The caller's `restaurantId` is taken from the verified token/profile, never from the path, query or body. A RestaurantOrder that belongs to another restaurant, or does not exist, returns `404 NOT_FOUND` to prevent enumeration.
 
**Allowed local transitions.**
 
| Current status | Action | Next status | Event published (via outbox) |
| --- | --- | --- | --- |
| PENDING | accept | ACCEPTED | RestaurantAccepted |
| PENDING | reject | REJECTED | RestaurantRejected |
| ACCEPTED | start preparation | PREPARING | RestaurantPreparing |
 
Any other combination returns `409 INVALID_TRANSITION`. REJECTED and PREPARING are final for this service (pickup belongs to Delivery).
 
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
 
**Atomic commit and asynchronous propagation.** Each successful action commits, in a single Restaurant Service database transaction: the new RestaurantOrder status and version, the idempotency record and the outbox entry for the corresponding event. The 200 response confirms only the local decision. Restaurant Service never calls Orders synchronously and never writes Orders tables; the global order status changes later when Orders consumes the event (Section 2.4). The event uses the stored workflow `correlationId` from the original order (Section 4.4), `causationId: null` because it is HTTP-triggered, `producer: "restaurants"`, `aggregateId` equal to `orderId`, and `aggregateVersion` equal to the new RestaurantOrder version. Payload details are defined in Section 4.2.
 
**Alignment with requirements.** A USER calling any decision endpoint receives 403 and no event is created (Feature 1, "Reject an unauthorized restaurant action"). A restaurant requesting another restaurant's order receives 404 (Feature 1, "Protect another restaurant's order"). Rejecting a paid order publishes RestaurantRejected, which leads Orders to REJECTED and one RefundPayment (Feature 4, "Refund a rejected paid order"). Preparation can only start after acceptance in this service, and Orders still waits durably if RestaurantPreparing arrives before RestaurantAccepted (Feature 4, "Handle preparation before acceptance").

### 3.6 Drivers and deliveries

Delivery Service owns Driver and Delivery. A Delivery is created with status `UNASSIGNED` only when Delivery consumes the `AssignDelivery` command that Orders sends after `RestaurantPreparing` (Section 2.4). Assignment is automatic: drivers do not choose orders, and turn-by-turn navigation is out of scope.

**Authorization.** All endpoints require the `DRIVER` role; any other role receives `403 FORBIDDEN` (Feature 1). The caller's `driverId` is taken from the verified token/profile, never from the path, query or body. A Delivery that is not currently or previously assigned to the caller, or does not exist, returns `404 NOT_FOUND`. The delivery address is visible only to the assigned driver.

**Assignment rules.**

- A driver is assignable when `onDuty = true` and `activeDeliveryId = null`. Assignment is one database transaction: a conditional update of the driver (`WHERE id = ? AND onDuty = true AND activeDeliveryId IS NULL AND version = ?`), the Delivery change to ASSIGNED and the DeliveryAssigned outbox entry. Only one delivery can win a driver, so a driver never has two active deliveries.
- Unassigned deliveries are served first-come, first-served by `createdAt`. Drivers are chosen by the longest time since their last assignment.
- When no driver is assignable, the Delivery stays `UNASSIGNED` and the order stays `PREPARING`. Assignment is retried whenever a driver goes on duty or completes a delivery, and every 10 seconds. A Delivery unassigned for more than 10 minutes raises an operational alert (Section 6.4) but is never cancelled automatically.
- A driver becomes assignable again when their delivery is completed, as long as they remain on duty.
- Going off duty never removes an active delivery: the driver finishes it and receives no new assignments.

**Allowed local transitions.**

| Current status | Action | Next status | Event published (via outbox) |
| --- | --- | --- | --- |
| UNASSIGNED | system assignment | ASSIGNED | DeliveryAssigned |
| ASSIGNED | driver confirms pickup | PICKED_UP | DeliveryPickedUp |
| PICKED_UP | driver confirms delivery | DELIVERED | DeliveryCompleted; driver's activeDeliveryId cleared |

Any other combination returns `409 INVALID_TRANSITION`. Assignment alone never means ON_THE_WAY: only DeliveryPickedUp moves the global order to ON_THE_WAY.

| Method and path | Authentication / authorization | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| GET /drivers/me | DRIVER; own record | None | 200 Driver; shared 4xx/5xx | Response: `{"id":"00000000-0000-4000-8000-000000000020","profileId":"00000000-0000-4000-8000-000000000021","onDuty":true,"activeDeliveryId":null,"version":4}`. |
| PUT /drivers/me/duty | DRIVER; own record; requires Idempotency-Key | `{onDuty: boolean, expectedVersion: integer}` | 200 Driver; 409 VERSION_CONFLICT or idempotency conflict; shared 4xx/5xx | Request: `{"onDuty":true,"expectedVersion":4}`. Response: Driver with `"onDuty":true,"version":5`. Setting the current value again returns 200 without change. |
| GET /deliveries | DRIVER; only deliveries assigned to the caller | None; pagination query plus optional `status: DeliveryStatus` filter | 200 paginated Delivery; shared 4xx/5xx | `GET /deliveries?status=ASSIGNED`. Response: `{"items":[{"id":"00000000-0000-4000-8000-000000000070","orderId":"00000000-0000-4000-8000-000000000010","restaurantId":"00000000-0000-4000-8000-000000000002","driverId":"00000000-0000-4000-8000-000000000020","deliveryAddress":{"line1":"123 Example Street","city":"Medellin"},"status":"ASSIGNED","version":2,"createdAt":"2026-10-13T15:05:00Z","updatedAt":"2026-10-13T15:05:03Z"}],"nextCursor":null}`. |
| GET /deliveries/{deliveryId} | DRIVER; assigned driver | None | 200 Delivery; 404 NOT_FOUND; shared 4xx/5xx | Response: the Delivery shown above. |
| POST /deliveries/{deliveryId}/pickup | DRIVER; assigned driver; requires Idempotency-Key | `{expectedVersion: integer}` | 200 Delivery with status PICKED_UP; 404 NOT_FOUND; 409 INVALID_TRANSITION, VERSION_CONFLICT or idempotency conflict; shared 4xx/5xx | Request: `{"expectedVersion":2}`. Response: Delivery with `"status":"PICKED_UP","version":3`. |
| POST /deliveries/{deliveryId}/completion | DRIVER; assigned driver; requires Idempotency-Key | `{expectedVersion: integer}` | 200 Delivery with status DELIVERED; 404 NOT_FOUND; 409 INVALID_TRANSITION, VERSION_CONFLICT or idempotency conflict; shared 4xx/5xx | Request: `{"expectedVersion":3}`. Response: Delivery with `"status":"DELIVERED","version":4`. |

`INVALID_TRANSITION` and `VERSION_CONFLICT` have the same meaning and `details` as in Section 3.5.

Example pickup request and response:

```http
POST /api/v1/deliveries/00000000-0000-4000-8000-000000000070/pickup HTTP/1.1
Authorization: Bearer <driver-access-token>
Content-Type: application/json
Idempotency-Key: 00000000-0000-4000-8000-000000000071
X-Correlation-ID: 00000000-0000-4000-8000-000000000072

{
  "expectedVersion": 2
}
```

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Correlation-ID: 00000000-0000-4000-8000-000000000072

{
  "id": "00000000-0000-4000-8000-000000000070",
  "orderId": "00000000-0000-4000-8000-000000000010",
  "restaurantId": "00000000-0000-4000-8000-000000000002",
  "driverId": "00000000-0000-4000-8000-000000000020",
  "deliveryAddress": {"line1": "123 Example Street", "city": "Medellin"},
  "status": "PICKED_UP",
  "version": 3,
  "createdAt": "2026-10-13T15:05:00Z",
  "updatedAt": "2026-10-13T15:20:00Z"
}
```

**Atomic commit and asynchronous propagation.** As in Section 3.5, each successful action commits the Delivery (and Driver when it changes), the idempotency record and the outbox entry in one Delivery database transaction. The 200 response confirms only the local action; Delivery never calls Orders synchronously. Events use the stored workflow `correlationId`, `causationId: null` for driver actions or the AssignDelivery messageId for system assignment, `producer: "delivery"`, `aggregateId` equal to the deliveryId and `aggregateVersion` equal to the new Delivery version.

**Alignment with requirements.** A non-driver calling these endpoints receives 403 (Feature 1). Assignment leaves the order PREPARING and only pickup moves it to ON_THE_WAY (Feature 4, "Progress through the mandatory order states"). Completion moves the order to DELIVERED and frees the driver (Feature 4, "Complete a delivery"). No assignable driver leaves the order PREPARING (Feature 4, "Wait for an available driver").

### 3.7 Internal service endpoints

Internal endpoints are not routed by the API Gateway. They require a service access token (OAuth 2.0 client credentials issued by Identity & Access) whose client is listed below; any other caller receives 403.

| Method and path | Allowed caller | Request body | Responses | Example |
| --- | --- | --- | --- | --- |
| GET /internal/v1/profiles/{profileId}/contact | Notifications | None | 200 `{profileId: UUID, displayName: string, email: string(email)}`; 404 NOT_FOUND; shared 4xx/5xx | Response: `{"profileId":"00000000-0000-4000-8000-000000000001","displayName":"Alex","email":"alex@example.test"}`. |

Notifications calls this endpoint only from its asynchronous consumer. A failure or timeout (2 seconds) is a transient handler failure retried by the policy in Section 4.3; it never affects orders. Contact data is not cached beyond one send attempt and never logged.

## 4. Asynchronous Events and Integration Contracts

### 4.1 Envelope and transport

Every command and event uses the common envelope below. Business fields are carried in `payload`, defined per message in Section 4.2 using the common types in Section 2.

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

All envelope fields are required except causationId, which may be null for a root operation. messageType must match a name in the Section 4.2 catalog. producer is `orders`, `payments`, `restaurants` or `delivery`. aggregateId identifies the producer-owned aggregate, aggregateVersion is its monotonically increasing local version, and orderId links the business workflow. A consumer must not compare versions from different producers as a global sequence. tracestate may optionally accompany traceparent as a string. Commands reference the requesting aggregate; they do not dictate the receiving aggregate's version.

RabbitMQ uses two durable topic exchanges, `eatsnow.events.v1` and `eatsnow.commands.v1`, with the case-sensitive messageType as routing key. Events describe completed facts and may have several consumers; commands request work from exactly one owner. Messages are published as persistent with publisher confirms, and consumers use manual acknowledgements (Section 4.3).

### 4.2 Message catalog

#### 4.2.1 Queues and bindings

Each consuming service has one durable queue. Consumers use a prefetch of 10 and acknowledge only after their database transaction commits.

| Queue | Exchange | Bound routing keys | Consumer |
| --- | --- | --- | --- |
| `orders.events` | `eatsnow.events.v1` | `OrderCreated`, `PaymentAuthorized`, `PaymentFailed`, `PaymentPendingReconciliation`, `PaymentRefunded`, `RestaurantAccepted`, `RestaurantRejected`, `RestaurantPreparing`, `DeliveryAssigned`, `DeliveryPickedUp`, `DeliveryCompleted` | Orders |
| `payments.commands` | `eatsnow.commands.v1` | `ChargePayment`, `RefundPayment` | Payments |
| `restaurants.commands` | `eatsnow.commands.v1` | `RequestRestaurantAcceptance` | Restaurants |
| `delivery.commands` | `eatsnow.commands.v1` | `AssignDelivery` | Delivery |
| `notifications.events` | `eatsnow.events.v1` | `OrderStatusChanged` | Notifications |

Every queue has a dead-letter queue `<queue>.dlq` bound to `eatsnow.dlx.v1` (Section 4.3, item 4).

#### 4.2.2 Message summary

All messages follow the default guarantees in Section 4.3: at-least-once delivery through the outbox, deduplication by `(consumer, messageId)`, per-aggregate serialization without global ordering, and transport retries after 5, 30 and 120 seconds before the DLQ. The last column lists only the additional rule each message adds.

| Name | Kind | Producer | Consumers / queue | Additional ordering, idempotency and retry rules |
| --- | --- | --- | --- | --- |
| OrderCreated | Event | Orders | Orders / `orders.events` | Sends ChargePayment once per order (outbox entry unique per orderId and command). |
| ChargePayment | Command | Orders | Payments / `payments.commands` | Unique Payment per orderId; the provider idempotency key is derived from orderId, so a duplicate command never creates a second charge. |
| PaymentAuthorized | Event | Payments | Orders / `orders.events` | Accepted only if `total` equals the order total; otherwise quarantined and alerted. |
| PaymentFailed | Event | Payments | Orders / `orders.events` | Emitted only for a definitive provider refusal, never for a timeout. |
| PaymentPendingReconciliation | Event | Payments | Orders / `orders.events` | Informational; may repeat. Never regresses a definitive payment status. |
| RefundPayment | Command | Orders | Payments / `payments.commands` | One logical refund per payment; the refund idempotency key is derived from paymentId. |
| PaymentRefunded | Event | Payments | Orders / `orders.events` | Accepted only if `amount` equals the authorized amount. |
| RequestRestaurantAcceptance | Command | Orders | Restaurants / `restaurants.commands` | Unique RestaurantOrder per orderId; a duplicate leaves the existing inbox entry unchanged. |
| RestaurantAccepted | Event | Restaurants | Orders / `orders.events` | Requires payment AUTHORIZED; otherwise stored as pending. |
| RestaurantRejected | Event | Restaurants | Orders / `orders.events` | Requires payment AUTHORIZED; otherwise stored as pending. Sends RefundPayment once. |
| RestaurantPreparing | Event | Restaurants | Orders / `orders.events` | Requires order ACCEPTED; otherwise stored as pending (Section 4.3, item 3). |
| AssignDelivery | Command | Orders | Delivery / `delivery.commands` | Unique Delivery per orderId. Waiting for a driver is normal pending business work, not a transport retry. |
| DeliveryAssigned | Event | Delivery | Orders / `orders.events` | Requires order PREPARING; otherwise stored as pending. |
| DeliveryPickedUp | Event | Delivery | Orders / `orders.events` | Requires DeliveryAssigned for the same deliveryId and driverId; otherwise stored as pending. |
| DeliveryCompleted | Event | Delivery | Orders / `orders.events` | Requires order ON_THE_WAY for the same deliveryId; otherwise stored as pending. |
| OrderStatusChanged | Event | Orders | Notifications / `notifications.events` | Notifications keeps the highest processed order version per order and acknowledges older versions without sending. Unique `(orderId, trigger)` prevents duplicate emails. |

#### 4.2.3 Payload schemas

The envelope carries `orderId`, `correlationId`, `causationId` and the producer's `aggregateId`/`aggregateVersion`; payloads do not repeat them. All payload fields are required unless marked optional. `schemaVersion` is 1 for every message. Consumers reject a message whose `orderId`, `restaurantId`, `driverId`, `deliveryId` or amount does not match the state they hold for that order; such messages are quarantined in the DLQ and alerted, never applied.

| Message | Aggregate (id / version) | Payload schema | Payload example |
| --- | --- | --- | --- |
| OrderCreated | Order | `customerId: UUID`, `restaurantId: UUID`, `items: OrderItem[1..50]`, `total: Money` | `{"customerId":"00000000-0000-4000-8000-000000000001","restaurantId":"00000000-0000-4000-8000-000000000002","items":[{"menuItemId":"00000000-0000-4000-8000-000000000003","name":"Classic Burger","quantity":1,"unitPrice":{"amountMinor":2500000,"currency":"COP"}}],"total":{"amountMinor":2500000,"currency":"COP"}}` |
| ChargePayment | Order | `total: Money`, `paymentMethodToken: string` (encrypted, see 4.2.5) | `{"total":{"amountMinor":2500000,"currency":"COP"},"paymentMethodToken":"<encrypted-for-payments>"}` |
| PaymentAuthorized | Payment | `paymentId: UUID`, `total: Money` | `{"paymentId":"00000000-0000-4000-8000-000000000080","total":{"amountMinor":2500000,"currency":"COP"}}` |
| PaymentFailed | Payment | `paymentId: UUID`, `total: Money`, `failureCode: "DECLINED" \| "INSUFFICIENT_FUNDS" \| "INVALID_PAYMENT_METHOD"` | `{"paymentId":"00000000-0000-4000-8000-000000000080","total":{"amountMinor":2500000,"currency":"COP"},"failureCode":"DECLINED"}` |
| PaymentPendingReconciliation | Payment | `paymentId: UUID`, `operation: "CHARGE" \| "REFUND"`, `pendingSince: timestamp` | `{"paymentId":"00000000-0000-4000-8000-000000000080","operation":"CHARGE","pendingSince":"2026-10-13T15:00:05Z"}` |
| RefundPayment | Order | `paymentId: UUID`, `amount: Money`, `reason: "RESTAURANT_REJECTED"` | `{"paymentId":"00000000-0000-4000-8000-000000000080","amount":{"amountMinor":2500000,"currency":"COP"},"reason":"RESTAURANT_REJECTED"}` |
| PaymentRefunded | Payment | `paymentId: UUID`, `amount: Money` | `{"paymentId":"00000000-0000-4000-8000-000000000080","amount":{"amountMinor":2500000,"currency":"COP"}}` |
| RequestRestaurantAcceptance | Order | `restaurantId: UUID`, `items: OrderItem[1..50]` | `{"restaurantId":"00000000-0000-4000-8000-000000000002","items":[{"menuItemId":"00000000-0000-4000-8000-000000000003","name":"Classic Burger","quantity":1,"unitPrice":{"amountMinor":2500000,"currency":"COP"}}]}` |
| RestaurantAccepted | RestaurantOrder | `restaurantId: UUID` | `{"restaurantId":"00000000-0000-4000-8000-000000000002"}` |
| RestaurantRejected | RestaurantOrder | `restaurantId: UUID`, `reason: string[1..300]` | `{"restaurantId":"00000000-0000-4000-8000-000000000002","reason":"Kitchen closed due to a gas outage"}` |
| RestaurantPreparing | RestaurantOrder | `restaurantId: UUID` | `{"restaurantId":"00000000-0000-4000-8000-000000000002"}` |
| AssignDelivery | Order | `restaurantId: UUID`, `deliveryAddress: Address` | `{"restaurantId":"00000000-0000-4000-8000-000000000002","deliveryAddress":{"line1":"123 Example Street","city":"Medellin"}}` |
| DeliveryAssigned | Delivery | `deliveryId: UUID`, `driverId: UUID` | `{"deliveryId":"00000000-0000-4000-8000-000000000070","driverId":"00000000-0000-4000-8000-000000000020"}` |
| DeliveryPickedUp | Delivery | `deliveryId: UUID`, `driverId: UUID`, `pickedUpAt: timestamp` | `{"deliveryId":"00000000-0000-4000-8000-000000000070","driverId":"00000000-0000-4000-8000-000000000020","pickedUpAt":"2026-10-13T15:20:00Z"}` |
| DeliveryCompleted | Delivery | `deliveryId: UUID`, `driverId: UUID`, `deliveredAt: timestamp` | `{"deliveryId":"00000000-0000-4000-8000-000000000070","driverId":"00000000-0000-4000-8000-000000000020","deliveredAt":"2026-10-13T15:45:00Z"}` |
| OrderStatusChanged | Order | `customerId: UUID`, `previousStatus: OrderStatus?`, `status: OrderStatus`, `paymentStatus: PaymentStatus` | `{"customerId":"00000000-0000-4000-8000-000000000001","previousStatus":"PREPARING","status":"ON_THE_WAY","paymentStatus":"AUTHORIZED"}` |

Complete example of a command, as published by Orders after PaymentAuthorized:

```json
{
  "messageId": "00000000-0000-4000-8000-000000000052",
  "messageType": "RequestRestaurantAcceptance",
  "schemaVersion": 1,
  "occurredAt": "2026-10-13T15:00:06Z",
  "producer": "orders",
  "orderId": "00000000-0000-4000-8000-000000000010",
  "correlationId": "00000000-0000-4000-8000-000000000040",
  "causationId": "00000000-0000-4000-8000-000000000053",
  "aggregateId": "00000000-0000-4000-8000-000000000010",
  "aggregateVersion": 2,
  "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-a3ce929d0e0e4736-01",
  "payload": {
    "restaurantId": "00000000-0000-4000-8000-000000000002",
    "items": [
      {
        "menuItemId": "00000000-0000-4000-8000-000000000003",
        "name": "Classic Burger",
        "quantity": 1,
        "unitPrice": {"amountMinor": 2500000, "currency": "COP"}
      }
    ]
  }
}
```

#### 4.2.4 Notification rules

Notifications sends one email per trigger: `ACCEPTED`, `ON_THE_WAY`, `DELIVERED`, `REJECTED` and `PAYMENT_FAILED` when `status` changes to that value, and `REFUNDED` when `paymentStatus` changes to REFUNDED. Other changes are acknowledged without sending. It resolves the customer's email through the internal contact endpoint (Section 3.7). The email provider call follows the same rule as payments: persist the attempt first, retry transient failures with the policy in Section 4.3, and mark the Notification FAILED after the last retry. A failed notification raises a metric and never changes the order.

#### 4.2.5 Sensitive fields

- `paymentMethodToken` appears only in ChargePayment. Orders encrypts it with the Payment Service public key before writing the outbox entry, so the broker, other consumers and logs never see it in plain text. Orders deletes its stored copy once Payments reports any payment outcome for the order.
- `deliveryAddress` appears only in AssignDelivery. Only Delivery is bound to that queue.
- Neither field, nor customer emails, may appear in logs, trace attributes or metric labels (Section 6.5). RabbitMQ users are per service and may only publish to their exchanges and read from their own queues.

### 4.3 Delivery guarantees, ordering and failures

1. **At least once:** persist business changes and outgoing messages in one database transaction. An outbox relay retries unconfirmed publication after 1, 5, 30 and then every 60 seconds until confirmed. Retain unconfirmed entries; alert on the oldest pending entry exceeding 60 seconds. A broker outage must not erase accepted orders.
2. **Idempotency:** each consumer atomically stores `(consumer, messageId)` with its business effects and outgoing messages. Acknowledge only after commit. Deduplication records live at least as long as their business aggregates and permitted replay history. Business constraints additionally prevent multiple payments, restaurant decisions or deliveries from semantically duplicate commands carrying new IDs.
3. **Ordering:** no global order or exactly-once claim is made. Serialize state writes per aggregate with a lock or optimistic version check. Store valid but premature messages durably as pending, acknowledge only after that storage commits, and reevaluate after each relevant transition and every 10 seconds. For example, RestaurantPreparing waits for RestaurantAccepted, and DeliveryPickedUp waits for DeliveryAssigned. Do not blindly discard lower-version messages whose prerequisites are still missing. Contradictory transitions are quarantined; pending messages older than 5 minutes raise an alert without silently changing state.
4. **Transport retries:** transient handler failures retry after 5, 30 and 120 seconds; after the third failed retry send to `<queue>.dlq` via the durable `eatsnow.dlx.v1` exchange, using the source queue name as routing key. Unknown schema versions and malformed messages go directly to that queue's DLQ. Redrive after repair preserves messageId, correlationId and causationId. Normal pending business work is not a transport failure.
5. **External payments:** payment API calls cannot be part of a database transaction. Persist the attempt before calling the adapter and always reuse the charge/refund idempotency key across crashes and retries. The adapter must support idempotent operations and status lookup by that key. On a 5-second timeout, persist an unknown outcome and reconcile every 30 seconds; never start a new charge with a new key. Alert after 5 minutes without a definitive outcome, but retain the pending attempt for recovery. Emit PaymentAuthorized, PaymentFailed or PaymentRefunded only after the definitive outcome is durably recorded with an outbox entry.
6. **Refund failures:** the order stays REJECTED and the payment stays REFUND_PENDING until refund confirmation. A refund timeout emits PaymentPendingReconciliation with operation REFUND and must not regress payment status to UNKNOWN for the original charge. Permanent provider refusal raises an operational alert and requires reconciliation; never claim the customer was refunded without confirmation.
7. **Evolution:** schemaVersion 1 consumers tolerate additive optional payload fields, validate required fields and reject incompatible changes. Breaking changes require a new schema version and explicit consumer migration.

### 4.4 Workflow correlation

The HTTP order request creates the workflow correlationId; Orders preserves it in every command. Each receiving service stores it alongside the local order reference. Later restaurant and driver actions use this stored ID for workflow messages, even when their HTTP request has a different correlation header. Log both requestCorrelationId and correlationId when they differ. causationId references the triggering message, or null for an HTTP-triggered action. A publication span injects traceparent; consumers extract it and create processing spans. Separate user actions start their own traces and link to stored workflow trace context; a correlation ID alone does not replace distributed tracing.

## 5. Requirements and Scenarios

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

#### Scenario: Protect another driver's delivery

- **WHEN** a DRIVER principal requests or acts on a delivery assigned to another driver
- **THEN** the system returns 404, discloses no delivery address and publishes no event.

#### Scenario: Provision restaurant and driver accounts centrally

- **WHEN** an ADMIN creates a DRIVER profile, and a USER attempts the same request
- **THEN** Identity creates the driver profile with a generated driverId for the ADMIN and returns 403 to the USER without creating any profile or role.

#### Scenario: Reject an invalid access token

- **WHEN** an access token is missing, expired or invalid
- **THEN** the Gateway rejects the request with 401 before business processing.

### Requirement: Feature 2 - Independent, responsive restaurant catalog

The system SHALL serve restaurant and menu queries independently of purchasing workflows, preserve catalog response contracts during supported cache failures, and provide bounded price staleness and reproducible read-performance acceptance criteria.

#### Scenario: Read the catalog during a downstream outage

- **WHEN** catalog entries are cached and Payments or Restaurant Service is unavailable for 30 seconds
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

#### Scenario: Create an order while Restaurant Service is unavailable

- **WHEN** a USER submits a valid quote and payment token while Restaurant Service is unavailable for 30 seconds
- **THEN** Orders commits the order/outbox and returns 202 with orderId, Location and correlationId without awaiting restaurant or payment processing.

#### Scenario: Recover accepted orders after a broker outage

- **WHEN** RabbitMQ is unavailable but the Orders database is healthy
- **THEN** order creation still returns 202 after commit, the outbox remains pending, and processing resumes after broker recovery without a second logical order or charge.

#### Scenario: Handle concurrent idempotent order requests

- **WHEN** two concurrent requests use the same subject, path, Idempotency-Key and body
- **THEN** at most one order is created; a retry receives the original 202 response or temporarily 409 REQUEST_IN_PROGRESS.

#### Scenario: Reconcile a lost payment response

- **WHEN** the provider charges successfully but its response is lost
- **THEN** Payments records UNKNOWN, reconciles using the same provider key, eventually records AUTHORIZED and never issues a second logical charge.

#### Scenario: Accept orders under load with downstream services stopped

- **WHEN** 10 valid order requests per second run for 60 seconds with healthy local persistence and downstream services stopped
- **THEN** target order-creation p95 latency is at most 500 ms and no accepted order lacks a durable outbox entry.

### Requirement: Feature 4 - Order transitions driven by asynchronous events

The system SHALL transition persisted orders through CREATED, ACCEPTED, PREPARING and ON_THE_WAY using asynchronous events and valid prerequisites, without skipping or regressing states. It SHALL handle delivery completion, duplicate or reordered events, paid-order rejection and pending driver assignment as specified below.

#### Scenario: Progress through the mandatory order states

- **WHEN** PaymentAuthorized, RestaurantAccepted, RestaurantPreparing, DeliveryAssigned and DeliveryPickedUp are processed with matching identities and valid prerequisites
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

#### Scenario: Assign one driver to concurrent deliveries

- **WHEN** two deliveries try to claim the same assignable driver at the same time
- **THEN** only one assignment commits and publishes DeliveryAssigned; the other delivery stays UNASSIGNED and is retried with another driver.

#### Scenario: Notify the customer without blocking transitions

- **WHEN** the order reaches ON_THE_WAY while the Notification Service or email provider is unavailable
- **THEN** the order transition commits normally, OrderStatusChanged stays queued or retried, and after recovery the customer receives the ON_THE_WAY email; redelivered messages create no additional Notification record or email.

### Requirement: Feature 5 - Correlated logs, metrics and distributed traces

The system SHALL expose correlated logs and distributed traces across the order lifecycle, distinguish definitive and ambiguous financial outcomes during investigation, and alert operators about failed messages and stalled publication without exposing sensitive data.

#### Scenario: Trace an order across participating services

- **WHEN** an order is created and processed through Payments, Restaurant Service and Delivery
- **THEN** structured logs from every participating service contain the same orderId and workflow correlationId, and Tempo exposes HTTP, publication and consumption spans linked through propagated trace context.

#### Scenario: Investigate an ambiguous payment outcome

- **WHEN** an operator investigates a payment timeout
- **THEN** Grafana allows searching Loki by orderId/correlationId and following traceId into Tempo; logs distinguish UNKNOWN, AUTHORIZED, FAILED and REFUNDED without treating a timeout or pending reconciliation as proof of a charge or refund.

#### Scenario: Detect failed or stalled message processing

- **WHEN** a message reaches a DLQ or an outbox entry stays pending over 60 seconds
- **THEN** an operational alert identifies the affected service/queue and investigation can recover the order's correlation context.

## 6. Observability and Verification Contract

This section defines the observable contract for Feature 5. For Case 6, distributed tracing is mandatory alongside metrics and logs.

### 6.1 Telemetry pipeline

| Signal | Producer | Transport | Backend |
| --- | --- | --- | --- |
| Traces | OpenTelemetry SDK in every service, including the API Gateway | OTLP to the OpenTelemetry Collector | Grafana Tempo |
| Logs | Structured JSON written to stdout by every service | OpenTelemetry Collector reads container logs | Grafana Loki |
| Metrics | Micrometer at `/actuator/prometheus` on each service's management port; RabbitMQ Prometheus plugin; PostgreSQL and Redis exporters | Prometheus scrape every 15 seconds | Prometheus |
| Dashboards and alerts | Grafana with Tempo, Loki and Prometheus data sources | — | Grafana |

The management port is not routed by the API Gateway. Grafana links Loki logs to Tempo by `traceId` and Tempo traces back to Loki by `traceId`.

### 6.2 Structured log schema

Every log line is one JSON object. Fields marked "workflow" are required on every line written while handling an order-related HTTP request or message.

| Field | Required | Description |
| --- | --- | --- |
| `timestamp` | Always | UTC RFC 3339 with milliseconds. |
| `level` | Always | `DEBUG`, `INFO`, `WARN` or `ERROR`. |
| `service` | Always | `gateway`, `identity`, `catalog`, `orders`, `payments`, `restaurants`, `delivery` or `notifications`. |
| `operation` | Always | HTTP route template (`POST /api/v1/orders`) or `consume <messageType>` / `publish <messageType>`. |
| `outcome` | Always | `SUCCESS`, `DUPLICATE`, `PENDING`, `REJECTED`, `RETRY` or `FAILURE`. |
| `traceId`, `spanId` | Always when a span is active | W3C trace context identifiers. |
| `requestCorrelationId` | HTTP | Value of `X-Correlation-ID` for this request. |
| `correlationId` | Workflow | Stored workflow correlation ID (Section 4.4). |
| `orderId` | Workflow | Global order ID. |
| `messageId`, `messageType`, `causationId` | Message handling | From the envelope. |
| `orderStatus`, `paymentStatus` | Orders and Payments, when changed | New status values after commit. |
| `httpStatus`, `durationMs` | HTTP | Response status and latency. |
| `errorCode` | On REJECTED / FAILURE | Error code from Section 3 or a handler error class; never a stack trace at INFO level. |

Example:

```json
{"timestamp":"2026-10-13T15:00:06.123Z","level":"INFO","service":"orders","operation":"consume PaymentAuthorized","outcome":"SUCCESS","traceId":"4bf92f3577b34da6a3ce929d0e0e4736","spanId":"a3ce929d0e0e4736","correlationId":"00000000-0000-4000-8000-000000000040","orderId":"00000000-0000-4000-8000-000000000010","messageId":"00000000-0000-4000-8000-000000000053","messageType":"PaymentAuthorized","causationId":"00000000-0000-4000-8000-000000000051","orderStatus":"CREATED","paymentStatus":"AUTHORIZED"}
```

### 6.3 Trace propagation and sampling

- The API Gateway starts or continues a trace from a valid `traceparent` and forwards it to the target service.
- Every outbox entry stores the `traceparent` of the transaction that created it. The relay creates a `publish <messageType>` producer span as a child of that context and writes it to the envelope.
- Each consumer extracts `traceparent` and creates a `consume <messageType>` consumer span as its child, so one order workflow appears as a single trace across services.
- Later restaurant and driver HTTP actions start their own traces. Their spans carry `eatsnow.order_id` and `eatsnow.correlation_id` attributes and a span link to the stored workflow trace context.
- Payment provider and email provider calls are client spans with `eatsnow.order_id` and the outcome, but no tokens or addresses.
- Sampling is parent-based, always on (100%) in the Wave 2 environment so every order can be traced. Any lower production ratio must be recorded in an ADR.

### 6.4 Metrics and alerts

Metric labels never include orderId, customerId, correlationId or other per-entity values.

| Metric | Type | Labels | Purpose |
| --- | --- | --- | --- |
| `http_server_requests_seconds` | Histogram | `service`, `method`, `uri`, `status` | Latency and error rate per route. |
| `eatsnow_orders_created_total` | Counter | — | Accepted orders (202). |
| `eatsnow_order_transitions_total` | Counter | `from`, `to` | Global order state transitions. |
| `eatsnow_outbox_pending` | Gauge | `service` | Unpublished outbox entries. |
| `eatsnow_outbox_oldest_pending_seconds` | Gauge | `service` | Age of the oldest unpublished entry. |
| `eatsnow_messages_consumed_total` | Counter | `service`, `message_type`, `outcome` | Processing results (SUCCESS, DUPLICATE, PENDING, RETRY, FAILURE). |
| `eatsnow_pending_messages_oldest_seconds` | Gauge | `service` | Age of the oldest premature message waiting for a prerequisite. |
| `eatsnow_payment_operations_total` | Counter | `operation`, `outcome` | Charges and refunds by AUTHORIZED, FAILED, UNKNOWN, REFUNDED. |
| `eatsnow_payment_unresolved_oldest_seconds` | Gauge | `operation` | Age of the oldest UNKNOWN or REFUND_PENDING payment. |
| `eatsnow_deliveries_unassigned_oldest_seconds` | Gauge | — | Age of the oldest UNASSIGNED delivery. |
| `eatsnow_catalog_cache_requests_total` | Counter | `result` | Redis `hit`, `miss` and `error`. |
| `eatsnow_notifications_total` | Counter | `trigger`, `outcome` | Emails SENT or FAILED. |
| `rabbitmq_queue_messages_ready` | Gauge | `queue` | Queue and DLQ depth (RabbitMQ plugin). |

| Alert | Condition | Severity |
| --- | --- | --- |
| OutboxStalled | `eatsnow_outbox_oldest_pending_seconds > 60` for 1 minute | Critical |
| MessagesInDLQ | `rabbitmq_queue_messages_ready{queue=~".*\\.dlq"} > 0` | Critical |
| PaymentUnresolved | `eatsnow_payment_unresolved_oldest_seconds > 300` | Critical |
| PrerequisiteWaitTooLong | `eatsnow_pending_messages_oldest_seconds > 300` | Warning |
| DeliveryUnassigned | `eatsnow_deliveries_unassigned_oldest_seconds > 600` | Warning |
| HighServerErrorRate | 5xx above 1% of requests for a service over 5 minutes | Warning |
| CatalogSlow | Catalog p95 above 300 ms over 5 minutes | Warning |
| CatalogCacheErrors | Redis `error` above 5% of catalog cache requests over 5 minutes | Warning |

Grafana provides at least these dashboards: Order flow (creations, transitions, pending and stuck orders), Messaging (outbox, queue and DLQ depth, consumer outcomes), Payments (outcomes and unresolved age), Catalog (latency, cache hit ratio) and Service health (HTTP latency and errors).

### 6.5 Sensitive data and telemetry failures

- Never log or add to spans or metrics: access tokens, quote tokens, payment method tokens, provider secrets, card data, delivery addresses or customer emails. Log `customerId`, not personal data.
- Logging libraries apply a deny-list for these fields as defense in depth.
- Telemetry export is asynchronous with bounded buffers. If the Collector, Loki, Tempo or Prometheus is unavailable, telemetry is dropped after the buffer fills and a local warning is logged; HTTP requests, transactions and message handling never wait for or fail because of telemetry.
- Grafana requires login; dashboards are read-only for non-administrators.

### 6.6 Order investigation procedure

1. Start from the `orderId` or the `X-Correlation-ID` returned to the customer.
2. In Grafana, query Loki with `{service=~".+"} | json | orderId="<orderId>"` to list every log line of the workflow in time order.
3. Open the `traceId` from the creation log in Tempo to see the HTTP, publication and consumption spans; follow span links for restaurant and driver actions.
4. Determine the payment outcome from the Payments log lines and `paymentStatus`:
   - `AUTHORIZED`: the provider confirmed the charge.
   - `FAILED`: definitive refusal; no charge exists.
   - `UNKNOWN`: outcome not yet known; reconciliation is running and the customer must not be told the charge failed.
   - `REFUND_PENDING` / `REFUNDED`: refund requested / confirmed by the provider.
5. If the order is stuck, check the Messaging dashboard for outbox age, queue depth and DLQ messages for the services involved.

### 6.7 Verification evidence

Wave 2 results are valid only with this evidence, recorded in the repository:

- Tested commit hash and the `docker-compose.yml` used, with service versions.
- Machine description (CPU, memory, operating system) and dataset size (restaurants, menu items, drivers).
- Load generator and script (for example k6) with the exact load used by each performance scenario.
- p50, p95 and p99 latency and error rate for each performance scenario, plus Grafana screenshots of the run.
- For each scenario in Section 5, the steps executed and the resulting logs, traces or HTTP responses.
- Only synthetic users, synthetic payment tokens and the payment and email test doubles; no real charges or emails.

| Feature | Scenarios to verify | Primary evidence |
| --- | --- | --- |
| 1 | Profile, restaurant/driver ownership, invalid token | HTTP responses and Gateway logs |
| 2 | Downstream outage, Redis fallback, price change, catalog load | HTTP responses, cache metrics, k6 results |
| 3 | Restaurant outage, broker outage, idempotency, lost payment response, order load | 202 responses, outbox metrics, Payments logs, k6 results |
| 4 | State progression, completion, reordering, duplicates, refund, driver wait and concurrency, notifications | Order state history, consumer metrics, Notification records |
| 5 | Order trace, payment investigation, DLQ/outbox alerts | Tempo trace, Loki query results, fired alerts |
