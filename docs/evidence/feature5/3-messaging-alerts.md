# Feature 5 - Detect failed or stalled message processing

Spec 5, Feature 5, scenario *Detect failed or stalled message processing*; alerts of Spec 6.4; procedure of Spec 6.6 step 5.

Recorded 2026-11-12 23:17 UTC on commit `1167075` (with uncommitted changes) by `scripts/feature5-evidence.ps1`.

## Steps

1. `docker compose stop rabbitmq`.
2. Alex places an order (`tok_ok`): the Orders Service accepts it and keeps the `ChargePayment` command in its outbox.
3. Wait until Prometheus fires `OutboxStalled` (oldest pending entry over 60 s for 1 minute).
4. `docker compose start rabbitmq`: the relay publishes the pending entries and the order continues.
5. Publish a `RequestRestaurantAcceptance` command for the same order without a restaurant or items (RabbitMQ management API). The Restaurant Service quarantines it in `restaurants.commands.dlq` without retrying.
6. Wait until Prometheus fires `MessagesInDLQ`, find the order's correlation context in Loki, then purge the DLQ.

## Result

| Check | Value |
|---|---|
| POST /orders with the broker down | 202 Accepted, orderId `c675c594-4d7b-4054-b9ed-ec63bdc02059`, correlationId `9c32ba5e-e172-475a-bdb2-fba5d5d139f2` |
| Order after the broker restart | status CREATED, paymentStatus AUTHORIZED |
| Pending outbox entries after recovery | 0 |
| Poison command routed | True |

## Fired alerts (Prometheus)

| Alert | Severity | Service / queue | State | Active since | Summary |
|---|---|---|---|---|---|
| OutboxStalled | critical | orders | firing | 2026-11-12T23:15:01.662182601Z | Outbox of orders has an entry pending for more than 60 s |
| MessagesInDLQ | critical | restaurants.commands.dlq | firing | 2026-11-12T23:17:16.662182601Z | Dead-letter queue restaurants.commands.dlq contains messages |

Outbox age while the broker was down:

| Labels | Oldest pending (s) |
|---|---|
| service=orders | 141 |

DLQ depth when `MessagesInDLQ` fired:

| Labels | Messages ready |
|---|---|
| queue=restaurants.commands.dlq, vhost=/ | 1 |

## Relay retries while the broker was down (Loki)

`{service="orders"} | json | operation=~"publish .*" | outcome!="SUCCESS"` (first 10 lines)

| timestamp | service | operation | outcome | messageId | retryInSeconds | traceId |
|---|---|---|---|---|---|---|
| 2026-11-12T23:13:49.593Z | orders | publish OrderCreated | RETRY | 03dce612-5501-4583-85e8-98ed764df073 | 1 |  |
| 2026-11-12T23:13:53.101Z | orders | publish OrderStatusChanged | RETRY | 2019a19f-ad57-44e6-bec7-79bbb15e9271 | 1 |  |
| 2026-11-12T23:13:59.110Z | orders | publish OrderStatusChanged | RETRY | 2019a19f-ad57-44e6-bec7-79bbb15e9271 | 5 |  |
| 2026-11-12T23:14:02.618Z | orders | publish OrderStatusChanged | RETRY | 2019a19f-ad57-44e6-bec7-79bbb15e9271 | 30 |  |
| 2026-11-12T23:14:08.625Z | orders | publish OrderCreated | RETRY | 03dce612-5501-4583-85e8-98ed764df073 | 5 |  |
| 2026-11-12T23:14:12.133Z | orders | publish OrderCreated | RETRY | 03dce612-5501-4583-85e8-98ed764df073 | 30 |  |
| 2026-11-12T23:14:29.681Z | orders | publish OrderStatusChanged | RETRY | 2019a19f-ad57-44e6-bec7-79bbb15e9271 | 60 |  |
| 2026-11-12T23:14:39.212Z | orders | publish OrderCreated | RETRY | 03dce612-5501-4583-85e8-98ed764df073 | 60 |  |
| 2026-11-12T23:15:29.840Z | orders | publish OrderStatusChanged | RETRY | 2019a19f-ad57-44e6-bec7-79bbb15e9271 | 60 |  |
| 2026-11-12T23:15:39.366Z | orders | publish OrderCreated | RETRY | 03dce612-5501-4583-85e8-98ed764df073 | 60 |  |

## DLQ log line with the order's correlation context (Loki)

`{service="restaurants"} | json | orderId="c675c594-4d7b-4054-b9ed-ec63bdc02059" | outcome="FAILURE"`

| timestamp | service | operation | outcome | messageType | errorCode | correlationId | messageId | traceId |
|---|---|---|---|---|---|---|---|---|
| 2026-11-12T23:16:54.085Z | restaurants | consume RequestRestaurantAcceptance | FAILURE | RequestRestaurantAcceptance | POISON_MESSAGE | 9c32ba5e-e172-475a-bdb2-fba5d5d139f2 | a4d0ecea-9bb2-4c0b-9483-2046defeca84 | 8042e5777879f504aa57b509605c21dd |
