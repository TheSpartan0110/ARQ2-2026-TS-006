# Services

Maven multi-module build (`services/pom.xml`). Java 17, Spring Boot 3.3.5. Architecture rules: see `AGENTS.md`.

| Module | Kind | Notes |
|---|---|---|
| `common` | library | Shared contracts and web infrastructure (`Money`, `MessageEnvelope`, filters, errors) |
| `messaging` | library | Reliable messaging toolkit: outbox, relay, idempotent consumer, trace propagation |
| `security` | library | Bearer-token verification for servlet services (Identity JWKS cache, pinned RS256, role gate) |
| `catalog`, `identity` | service | Catalog and signed quotes; profiles and tokens |
| `orders`, `payments` | service | Order orchestration and the payment provider integration |
| `restaurants`, `delivery`, `notifications` | service | Restaurant inbox, driver assignment, customer emails |
| `gateway` | service | Reactive Spring Cloud Gateway: JWT validation, routing, correlation |

## The order lifecycle

```
Customer   POST /orders ---------------------------> Orders  (order + outbox in ONE transaction, HTTP 202)
Orders     OrderCreated -> ChargePayment ----------> Payments (attempt PENDING, provider call outside any transaction)
Payments   PaymentAuthorized ----------------------> Orders  (paymentStatus AUTHORIZED) -> RequestRestaurantAcceptance
Restaurants inbox entry PENDING <------------------- Orders
Restaurant accept / reject / preparation (HTTP) ---> RestaurantAccepted | RestaurantRejected | RestaurantPreparing
Orders     ACCEPTED -> PREPARING -> AssignDelivery -> Delivery (UNASSIGNED, automatic assignment to a free driver)
Delivery   DeliveryAssigned (order stays PREPARING) -> driver pickup -> DeliveryPickedUp (order ON_THE_WAY)
           -> driver completion -> DeliveryCompleted (order DELIVERED, driver freed)
Reject     RestaurantRejected -> order REJECTED, RefundPayment once -> PaymentRefunded (paymentStatus REFUNDED)
Orders     OrderStatusChanged on every change ------> Notifications (one email per trigger, never blocks the order)
```

Every arrow is a message through RabbitMQ written to a transactional outbox in the sender's own transaction. Each consumer
stores `(consumer, messageId)` in the same transaction as its business effects and acknowledges only after the commit.
Orders serializes writes per order (row lock) and stores valid but premature events durably (`pending_events`),
re-evaluating them after every transition and every 10 seconds; contradictory events are quarantined in the DLQ.

## Run

```powershell
docker compose up -d --build        # first build takes several minutes (Maven runs inside Docker)
docker compose ps
./scripts/dev-order.ps1             # Step 2 demo: login -> quote -> order -> follows the payment
./scripts/dev-flow.ps1              # full lifecycle with customer, restaurant and driver
```

The gateway listens on **http://localhost:8090** (catalog and identity keep their direct ports 8080 and 8082).

Build and test without Docker (JDK 17 + Maven): `cd services && mvn clean verify`.

## Full-flow demos (`scripts/dev-flow.ps1`)

| Command | Expected |
|---|---|
| `./scripts/dev-flow.ps1` | order reaches `DELIVERED`; the customer receives emails for ACCEPTED, ON_THE_WAY and DELIVERED in Mailpit |
| `./scripts/dev-flow.ps1 -Scenario Reject` | order `REJECTED`, `paymentStatus` goes `REFUND_PENDING` then `REFUNDED`; emails for REJECTED and REFUNDED |
| `-Scenario Reject -PaymentToken tok_refund_lost` | the refund response is lost: `REFUND_PENDING` until reconciliation (~30 s), then `REFUNDED` |
| `-Scenario Reject -PaymentToken tok_refund_refused` | permanent refusal: stays `REFUND_PENDING`, an operational alert is raised, never reported as refunded |

Seed users (password `password123`): `alex@example.test` (USER), `elena@example.test` (RESTAURANT, restaurant ...0050),
`sam@example.test` (DRIVER, driver ...0020), `admin@eatsnow.test` (ADMIN).

## Payment scenarios (`scripts/dev-order.ps1 -PaymentToken`)

| Token | What it proves | Expected |
|---|---|---|
| `tok_ok` | Happy path | 202, then `paymentStatus=AUTHORIZED` within about a second |
| `tok_declined` | Definitive refusal | `status=PAYMENT_FAILED`, `paymentStatus=FAILED` |
| `tok_lost_response` | Lost provider response (Spec Feature 3) | `UNKNOWN`, then `AUTHORIZED` after reconciliation (~30 s), one charge only |
| `tok_timeout` | Unresolved payment | stays `UNKNOWN`; `PaymentUnresolved` alert after 5 minutes |

Resilience checks:

```powershell
docker compose stop rabbitmq               # broker outage
./scripts/dev-order.ps1 -WaitSeconds 5     # still HTTP 202: order and outbox entry are committed
docker compose start rabbitmq              # the outbox drains and the order reaches AUTHORIZED
./scripts/dev-order.ps1 -Replay            # same Idempotency-Key: the original response, one order
```

Where to look: Grafana -> Explore -> Loki `{service=~".+"} | json | orderId="<id>"`, then follow `traceId` into Tempo.
RabbitMQ UI -> Queues (`*.dlq` must stay empty). Prometheus -> `eatsnow_outbox_pending`, `eatsnow_messages_consumed_total`.

## Known limitations (to be addressed in later steps)

| Item | Status | Planned |
|---|---|---|
| Payment token encryption | One shared AES key (Spec asks for the Payments public key) | Asymmetric encryption |
| Internal endpoint auth | Identity's `/internal/v1/profiles/{id}/contact` uses a shared `X-Service-Key` (Spec 3.7 asks for client-credentials tokens) | Client-credentials tokens issued by Identity |
| Identity `aud` claim | Identity issues none, so the audience is not checked | Add `aud` and verify it |
| Signing keys | Identity and Catalog generate their RSA keys at startup; verifiers refresh the JWKS once when a key stops verifying | Persistent keys |
| `JwksKeySource` | Duplicated in `security` and `gateway` (the gateway is reactive and cannot use `common`) | Optional: reactive variant |
| Payments replicas | One replica assumed by the payment worker | Claim rows with `SKIP LOCKED` |
| Unroutable publish | Not detected (no mandatory/returns handling) | Publisher returns |
| Traces of separate user actions | Restaurant and driver HTTP actions start their own trace; they are not linked to the order's stored trace context | Span links |
| Rate limiting (429) | Not implemented in the gateway | Gateway filter |
| Grafana dashboards | Only the infrastructure dashboard; the 5 dashboards of Spec 6.4 and the k6 evidence (6.7) are pending | Step 4.6 |
