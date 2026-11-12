# Feature 5 - Investigate an ambiguous payment outcome

Spec 5, Feature 5, scenario *Investigate an ambiguous payment outcome*; procedure of Spec 6.6 step 4.

Recorded 2026-11-12 23:13 UTC on commit `1167075` (with uncommitted changes) by `scripts/feature5-evidence.ps1`.

## Steps

1. Alex places two orders: one paid with `tok_lost_response` (the provider charges but its answer is lost) and one with `tok_declined` (definitive refusal).
2. `GET /api/v1/orders/{id}` is polled for both orders.
3. Loki: `{service="payments"} | json | orderId="db718e97-dda3-4337-8449-74553efdca73"` and `{service="payments"} | json | orderId="b9225d82-585a-45ec-9fe6-68fa48c3f55b"`.

## Result

| Order | Payment token | POST /orders | paymentStatus seen | Final order status | Final paymentStatus |
|---|---|---|---|---|---|
| `db718e97-dda3-4337-8449-74553efdca73` | `tok_lost_response` | 202 | UNKNOWN, then AUTHORIZED | CREATED | AUTHORIZED |
| `b9225d82-585a-45ec-9fe6-68fa48c3f55b` | `tok_declined` | 202 | FAILED | PAYMENT_FAILED | FAILED |

While the lost response was `UNKNOWN`, the order stayed `CREATED`: an unknown outcome is not treated as a failed charge, and the customer was not told the payment failed. Reconciliation then confirmed the charge (`AUTHORIZED`). The declined card is a definitive `FAILED`, so no charge exists.

Highest `eatsnow_payment_unresolved_oldest_seconds` scraped while the charge was UNKNOWN (`max_over_time` over 5 minutes; the `PaymentUnresolved` alert fires above 300 s):

| Labels | Oldest unresolved (s) |
|---|---|
| operation=CHARGE, service=payments | 15 |

## Payments log lines: lost response (UNKNOWN, then reconciled)

| timestamp | service | operation | outcome | paymentStatus | errorCode | traceId |
|---|---|---|---|---|---|---|
| 2026-11-12T23:13:24.507Z | payments | consume ChargePayment | SUCCESS |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:24.877Z | payments | charge payment | SUCCESS | UNKNOWN |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:24.890Z | payments | publish PaymentPendingReconciliation | SUCCESS |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:42.324Z | payments | charge payment | SUCCESS | AUTHORIZED |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:42.490Z | payments | publish PaymentAuthorized | SUCCESS |  |  | 3b634b0f529bd067f3f91670681ed673 |

## Orders log lines: lost response

| timestamp | service | operation | outcome | messageType | orderStatus | paymentStatus | traceId |
|---|---|---|---|---|---|---|---|
| 2026-11-12T23:13:23.895Z | orders | create order | SUCCESS |  |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:23.980Z | orders | publish OrderCreated | SUCCESS | OrderCreated |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:23.985Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:23.985Z | orders | consume OrderCreated | SUCCESS | OrderCreated |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:24.505Z | orders | publish ChargePayment | SUCCESS | ChargePayment |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:24.894Z | orders | order transition | SUCCESS | PaymentPendingReconciliation | CREATED | UNKNOWN | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:24.896Z | orders | consume PaymentPendingReconciliation | SUCCESS | PaymentPendingReconciliation |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:25.015Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:42.494Z | orders | order transition | SUCCESS | PaymentAuthorized | CREATED | AUTHORIZED | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:42.500Z | orders | consume PaymentAuthorized | SUCCESS | PaymentAuthorized |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:42.573Z | orders | publish OrderStatusChanged | SUCCESS | OrderStatusChanged |  |  | 3b634b0f529bd067f3f91670681ed673 |
| 2026-11-12T23:13:42.578Z | orders | publish RequestRestaurantAcceptance | SUCCESS | RequestRestaurantAcceptance |  |  | 3b634b0f529bd067f3f91670681ed673 |

## Payments log lines: declined card (definitive FAILED)

| timestamp | service | operation | outcome | paymentStatus | errorCode | traceId |
|---|---|---|---|---|---|---|
| 2026-11-12T23:13:24.511Z | payments | consume ChargePayment | SUCCESS |  |  | 590eb0190ec704976035006089d3cd24 |
| 2026-11-12T23:13:24.882Z | payments | charge payment | SUCCESS | FAILED |  | 590eb0190ec704976035006089d3cd24 |
| 2026-11-12T23:13:24.894Z | payments | publish PaymentFailed | SUCCESS |  |  | 590eb0190ec704976035006089d3cd24 |
