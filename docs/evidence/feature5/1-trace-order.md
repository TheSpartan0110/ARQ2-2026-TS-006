# Feature 5 - Trace an order across participating services

Spec 5, Feature 5, scenario *Trace an order across participating services*; procedure of Spec 6.6 steps 1-3.

Recorded 2026-11-12 23:13 UTC on commit `1167075` (with uncommitted changes) by `scripts/feature5-evidence.ps1`.

## Steps

1. `scripts/dev-flow.ps1`: Alex orders from Elena Bistro (`tok_ok`), Elena accepts and starts preparation, Sam picks up and completes the delivery.
2. Loki: `{service=~".+"} | json | orderId="404c1e86-2903-4cdd-b665-75f0cce15700"`
3. Jaeger: spans tagged `eatsnow.correlation_id=f90a5873-a5fc-48a5-a2d4-5d01f148b405` in the services orders, restaurants and delivery.

## Result

| Check | Value |
|---|---|
| orderId | `404c1e86-2903-4cdd-b665-75f0cce15700` |
| correlationId | `f90a5873-a5fc-48a5-a2d4-5d01f148b405` |
| X-Trace-Id of POST /orders | `fd1b75b91006b110e611da213b3475f2` |
| Services with log lines for the order | delivery, notifications, orders, payments, restaurants |
| Distinct correlationId values in those lines | 1 (`f90a5873-a5fc-48a5-a2d4-5d01f148b405`) |
| Log lines | 51 |
| Traces found by correlation id | 5 |
| Spans with a link to an earlier workflow trace | 4 |

## Traces (Jaeger)

The order creation trace contains the HTTP spans and every `publish`/`consume` span until the restaurant inbox. Restaurant and driver actions start their own trace (Spec 6.3) and link back to the trace that the service received for the order.

| Trace | Started by | Spans | Services | Span links |
|---|---|---|---|---|
| `fd1b75b91006b110e611da213b3475f2` | orders: http post /api/v1/orders | 16 | gateway, notifications, orders, payments, restaurants |  |
| `309f93b1c1952c71ed2d9ac19d1ed5c2` | restaurants: http post /api/v1/restaurant-orders/{orderId}/accept | 6 | gateway, orders, restaurants | -> `fd1b75b91006b110e611da213b3475f2` |
| `26acc02aa05f08c50f1beb94f34eb78c` | restaurants: http post /api/v1/restaurant-orders/{orderId}/preparation | 7 | delivery, gateway, orders, restaurants | -> `fd1b75b91006b110e611da213b3475f2` |
| `3048d892053ffe251f56802d942f777b` | delivery: http post /api/v1/deliveries/{deliveryId}/pickup | 4 | delivery, gateway | -> `26acc02aa05f08c50f1beb94f34eb78c` |
| `4d98b3df7c9d106bd7c5013830ba243c` | delivery: http post /api/v1/deliveries/{deliveryId}/completion | 2 | delivery | -> `26acc02aa05f08c50f1beb94f34eb78c` |

### Spans of the creation trace `fd1b75b91006b110e611da213b3475f2`

```text
gateway: http post
notifications: consume OrderStatusChanged
orders: consume OrderCreated
orders: consume PaymentAuthorized
orders: http post /api/v1/orders
orders: publish ChargePayment
orders: publish OrderCreated
orders: publish OrderStatusChanged
orders: publish RequestRestaurantAcceptance
payments: charge payment
payments: consume ChargePayment
payments: publish PaymentAuthorized
restaurants: consume RequestRestaurantAcceptance
```

## Log lines (Loki)

| timestamp | service | operation | outcome | messageType | orderStatus | paymentStatus | correlationId | traceId |
|---|---|---|---|---|---|---|---|---|
| 2026-11-12T23:13:16.572Z | orders | create order | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:16.907Z | orders | publish OrderCreated | SUCCESS | OrderCreated |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:16.911Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:16.912Z | orders | consume OrderCreated | SUCCESS | OrderCreated |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:16.915Z | notifications | consume OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.421Z | orders | publish ChargePayment | SUCCESS | ChargePayment |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.423Z | payments | consume ChargePayment | SUCCESS | ChargePayment |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.834Z | payments | charge payment | SUCCESS |  |  | AUTHORIZED | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.843Z | payments | publish PaymentAuthorized | SUCCESS | PaymentAuthorized |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.847Z | orders | order transition | SUCCESS | PaymentAuthorized | CREATED | AUTHORIZED | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.853Z | orders | consume PaymentAuthorized | SUCCESS | PaymentAuthorized |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.929Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.931Z | notifications | consume OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.933Z | orders | publish RequestRestaurantAcceptance | SUCCESS | RequestRestaurantAcceptance |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:17.938Z | restaurants | consume RequestRestaurantAcceptance | SUCCESS | RequestRestaurantAcceptance |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | fd1b75b91006b110e611da213b3475f2 |
| 2026-11-12T23:13:18.108Z | restaurants | restaurant accept | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 309f93b1c1952c71ed2d9ac19d1ed5c2 |
| 2026-11-12T23:13:18.130Z | restaurants | publish RestaurantAccepted | SUCCESS | RestaurantAccepted |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 309f93b1c1952c71ed2d9ac19d1ed5c2 |
| 2026-11-12T23:13:18.134Z | orders | order transition | SUCCESS | RestaurantAccepted | ACCEPTED | AUTHORIZED | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 309f93b1c1952c71ed2d9ac19d1ed5c2 |
| 2026-11-12T23:13:18.137Z | orders | consume RestaurantAccepted | SUCCESS | RestaurantAccepted |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 309f93b1c1952c71ed2d9ac19d1ed5c2 |
| 2026-11-12T23:13:18.440Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 309f93b1c1952c71ed2d9ac19d1ed5c2 |
| 2026-11-12T23:13:18.442Z | notifications | record notification | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 309f93b1c1952c71ed2d9ac19d1ed5c2 |
| 2026-11-12T23:13:18.444Z | notifications | consume OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 309f93b1c1952c71ed2d9ac19d1ed5c2 |
| 2026-11-12T23:13:18.606Z | notifications | send notification | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 |  |
| 2026-11-12T23:13:18.865Z | restaurants | restaurant preparation | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.143Z | restaurants | publish RestaurantPreparing | SUCCESS | RestaurantPreparing |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.144Z | orders | order transition | SUCCESS | RestaurantPreparing | PREPARING | AUTHORIZED | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.149Z | orders | consume RestaurantPreparing | SUCCESS | RestaurantPreparing |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.448Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.450Z | notifications | consume OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.452Z | orders | publish AssignDelivery | SUCCESS | AssignDelivery |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.465Z | delivery | assign delivery | SUCCESS | AssignDelivery |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.467Z | delivery | consume AssignDelivery | SUCCESS | AssignDelivery |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.661Z | delivery | delivery pickup | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 3048d892053ffe251f56802d942f777b |
| 2026-11-12T23:13:19.875Z | delivery | publish DeliveryAssigned | SUCCESS | DeliveryAssigned |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.876Z | orders | order assignment | SUCCESS | DeliveryAssigned | PREPARING |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.880Z | delivery | publish DeliveryPickedUp | SUCCESS | DeliveryPickedUp |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 3048d892053ffe251f56802d942f777b |
| 2026-11-12T23:13:19.880Z | orders | consume DeliveryAssigned | SUCCESS | DeliveryAssigned |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 26acc02aa05f08c50f1beb94f34eb78c |
| 2026-11-12T23:13:19.885Z | orders | order transition | SUCCESS | DeliveryPickedUp | ON_THE_WAY | AUTHORIZED | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 3048d892053ffe251f56802d942f777b |
| 2026-11-12T23:13:19.889Z | orders | consume DeliveryPickedUp | SUCCESS | DeliveryPickedUp |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 3048d892053ffe251f56802d942f777b |
| 2026-11-12T23:13:19.959Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 3048d892053ffe251f56802d942f777b |
| 2026-11-12T23:13:19.961Z | notifications | record notification | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 3048d892053ffe251f56802d942f777b |
| 2026-11-12T23:13:19.962Z | notifications | consume OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 3048d892053ffe251f56802d942f777b |
| 2026-11-12T23:13:20.435Z | delivery | delivery completion | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 4d98b3df7c9d106bd7c5013830ba243c |
| 2026-11-12T23:13:20.635Z | notifications | send notification | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 |  |
| 2026-11-12T23:13:20.890Z | delivery | publish DeliveryCompleted | SUCCESS | DeliveryCompleted |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 4d98b3df7c9d106bd7c5013830ba243c |
| 2026-11-12T23:13:20.894Z | orders | order transition | SUCCESS | DeliveryCompleted | DELIVERED | AUTHORIZED | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 4d98b3df7c9d106bd7c5013830ba243c |
| 2026-11-12T23:13:20.897Z | orders | consume DeliveryCompleted | SUCCESS | DeliveryCompleted |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 4d98b3df7c9d106bd7c5013830ba243c |
| 2026-11-12T23:13:20.967Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 4d98b3df7c9d106bd7c5013830ba243c |
| 2026-11-12T23:13:20.969Z | notifications | record notification | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 4d98b3df7c9d106bd7c5013830ba243c |
| 2026-11-12T23:13:20.971Z | notifications | consume OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 | 4d98b3df7c9d106bd7c5013830ba243c |
| 2026-11-12T23:13:21.659Z | notifications | send notification | SUCCESS |  |  |  | f90a5873-a5fc-48a5-a2d4-5d01f148b405 |  |
