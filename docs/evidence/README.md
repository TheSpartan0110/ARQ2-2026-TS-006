# Wave 2 verification evidence

Evidence required by [Spec 6.7](../spec.md#67-verification-evidence). Synthetic users and payment tokens only; the payment provider and the email provider are test doubles (no real charges or emails).

## Environment

The environment of every load run is recorded automatically in [`load/results/environment.md`](../../load/results/environment.md): tested commit, machine (CPU, memory, operating system), Docker engine, image of every container and dataset size.

| Item | Value |
| --- | --- |
| Machine | AMD Ryzen 7 7730U, 8 cores / 16 threads, 39.4 GB RAM, Windows 11 Pro 10.0.26200 |
| Docker engine | Docker Desktop 29.9.2, 16 CPUs and 19.2 GB for all containers (services, databases, broker and observability stack on the same machine) |
| Dataset | 2 restaurants, 5 menu items, 1 driver (seed data) |
| Load generator | k6 `grafana/k6:0.54.0`, running in the compose network and calling the API Gateway |
| Authentication | Real OIDC login (authorization code + PKCE) of the synthetic customer `alex@example.test` |

## Performance scenarios (Spec 5)

Reproduce with one command (about 4 minutes; the order scenario stops and restarts the downstream services itself):

```powershell
./load/run-load.ps1 -Scenario all
```

Scripts: [`load/k6/`](../../load/k6/). Each run writes a Markdown summary and the raw k6 JSON to `load/results/`, and streams its metrics to Prometheus (Grafana dashboard **EatsNow - Load tests**, filter by k6 run).

### Feature 2 - Read a warmed catalog during concurrent purchasing

**WHEN** 50 concurrent clients read a warmed catalog while 10 orders per second are submitted for 60 seconds, **THEN** the catalog p95 latency is at most 300 ms with less than 1% server errors; compared with the read-only baseline.

Each client reads the restaurant list and a menu, then waits 0.1 s. The cache is warmed in the k6 `setup()` phase.

| Run | Request | Requests | p50 (ms) | p95 (ms) | p99 (ms) | Errors | Target | Result |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| Baseline (read-only) | GET /restaurants | 27,828 | 2.8 | 5.3 | 10.2 | 0.00% | p95 ≤ 300 ms | PASS |
| Baseline (read-only) | GET /restaurants/{id}/menu | 27,828 | 3.5 | 6.5 | 12.0 | 0.00% | p95 ≤ 300 ms | PASS |
| With 10 orders/s | GET /restaurants | 26,750 | 3.7 | 13.4 | 26.2 | 0.00% | p95 ≤ 300 ms | PASS |
| With 10 orders/s | GET /restaurants/{id}/menu | 26,750 | 4.5 | 15.5 | 28.5 | 0.00% | p95 ≤ 300 ms | PASS |
| With 10 orders/s | POST /orders | 600 | 12.4 | 28.0 | 39.2 | 0.00% | p95 ≤ 500 ms | PASS |

Purchasing raises the catalog p95 from about 6 ms to about 15 ms, far below the 300 ms target: catalog reads are served from Redis and do not touch the purchasing workflow.

### Feature 3 - Accept orders under load with downstream services stopped

**WHEN** 10 valid order requests per second run for 60 seconds with healthy local persistence and downstream services stopped, **THEN** the order-creation p95 latency is at most 500 ms and no accepted order lacks a durable outbox entry.

Payments, Restaurant, Delivery and Notifications are stopped before the run. Each iteration requests a quote and then the order.

| Request | Requests | p50 (ms) | p95 (ms) | p99 (ms) | Errors | Target | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| POST /orders | 600 | 7.0 | 9.0 | 13.9 | 0.00% | p95 ≤ 500 ms | PASS |
| POST /quotes (prerequisite) | 600 | 7.7 | 10.0 | 14.5 | 0.00% | informative | - |

Durability check in the Orders database after the run, with the downstream services still stopped:

| Check | Result |
| --- | --- |
| Orders accepted (202) during the run | 600 |
| Accepted orders without an `OrderCreated` outbox entry | 0 |
| Unpublished outbox entries (the relay handed every message to RabbitMQ; the work waited in the queues) | 0 |

After the services were started again, every order of the run reached `paymentStatus = AUTHORIZED`, all queues drained and no message reached a DLQ. The orders stay `CREATED` because the load uses Burger House, which has no restaurant user to accept them.

## Dashboards (Spec 6.4)

Grafana (`http://localhost:3000`), folder **EatsNow**. The dashboards are provisioned from [`infra/grafana/dashboards/`](../../infra/grafana/dashboards/) and are read-only in the UI.

| Dashboard | Shows | Used for |
| --- | --- | --- |
| Order flow | Accepted orders, transitions per minute, `POST /orders` latency, waiting ages (premature events, unassigned deliveries, unresolved payments, outbox), notifications | Features 3 and 4 |
| Messaging | DLQ depth, outbox pending and age per service, work, retry and DLQ queues, consumer outcomes (SUCCESS, DUPLICATE, PENDING, RETRY, FAILURE), firing alerts | Features 3, 4 and 5 |
| Payments | Charges AUTHORIZED, FAILED and UNKNOWN, refunds, unresolved age, payment logs from Loki | Features 3 and 5 |
| Catalog | Catalog p95, cache hit ratio, Redis errors and availability, quote latency | Feature 2 |
| Service health | Availability, request rate, 5xx ratio and p95 per service and route, CPU, heap, DB connections, service graph | All |
| Load tests | k6 request rate, p50, p95 and p99, failed ratio and VUs per run, next to the server-side latency, outbox and queues | Spec 6.7 |

### Dashboard pictures

The pictures of the recorded run are in [`load/results/screenshots/`](../../load/results/screenshots/README.md), each with what it proves. They were rendered once from these dashboards with the Grafana image renderer, which is not part of the compose stack; for a new run, take the same panels from Grafana with the run's time range.

| # | Picture | Proves |
| --- | --- | --- |
| 1-3 | k6 p95 latency of each run | Catalog reads and order creation stay far below their targets |
| 4-5 | Catalog latency and Redis cache hits during purchasing | Feature 2: purchasing does not slow the catalog; reads come from the cache |
| 6 | k6 orders per second with downstream services stopped | Feature 3: a steady 10 orders/s accepted with HTTP 202 |
| 7 | RabbitMQ work queues | The work waits in the queues while the services are down and drains when they return |
| 8 | Unpublished outbox entries | The outbox handed every accepted order to RabbitMQ |
| 9 | Retry queues and DLQs | No message failed during the run or the recovery |
| 10 | Payments confirmed to Orders (running total) | Flat while Payments is down, then +600: every queued order is charged once when it comes back |
