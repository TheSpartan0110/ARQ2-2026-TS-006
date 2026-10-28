# EatsNow — Postman API Collections

This directory contains ready-to-import Postman collections for testing EatsNow microservices independently and via the API Gateway.

---

## 1. Files in this Directory

| File | Target Service | Description |
|---|---|---|
| [`identity.postman_collection.json`](identity.postman_collection.json) | Identity & Access Service | Authentication (RS256 JWT), public JWKS, self-profile (`/profiles/me`), and admin profile management (`/admin/profiles`). |
| [`catalog.postman_collection.json`](catalog.postman_collection.json) | Catalog Service | Restaurant listings, menu retrieval, and checkout quote generation (`/quotes`) with signed tokens. |
| [`eatsnow_local.postman_environment.json`](eatsnow_local.postman_environment.json) | All services | Local environment variables (`baseUrl`, ports, test UUIDs). |

---

## 2. Default Ports & Addresses (Docker Compose)

| Service | Application Port (HTTP) | Management Port (Prometheus / Health) |
|---|---|---|
| **Identity Service** | `http://localhost:8082` | `http://localhost:8083` |
| **Catalog Service** | `http://localhost:8080` | `http://localhost:8081` |
| **PostgreSQL** | `localhost:5432` | — |
| **Redis** | `localhost:6379` | — |

---

## 3. How to Use with Postman

### Step 1: Import Files
1. Open Postman.
2. Click **Import** (top left).
3. Select and drag all files from `docs/requests/`:
   - `identity.postman_collection.json`
   - `catalog.postman_collection.json`
   - `eatsnow_local.postman_environment.json`

### Step 2: Automated Token Flow
The collections include test scripts that automatically capture tokens upon login:
1. Run **Authentication & OIDC $\rightarrow$ 1. Login User**.
   - Authenticates `alex@example.test` / `password123`.
   - Postman automatically extracts `accessToken` and saves it to `{{userToken}}`.
2. Run **Authentication & OIDC $\rightarrow$ 2. Login Admin**.
   - Authenticates `admin@eatsnow.test` / `password123`.
   - Postman automatically extracts `accessToken` and saves it to `{{adminToken}}`.
3. All subsequent requests in the collection (`GET /profiles/me`, `POST /admin/profiles`, etc.) automatically use the saved bearer token.

---

## 4. Built-in Standards

- **Idempotency:** Mutating endpoints (`POST`, `PATCH`) automatically generate a fresh UUID using Postman's dynamic variable `{{$guid}}` for the `Idempotency-Key` header.
- **Traceability:** Requests send `X-Correlation-ID: {{$guid}}` to trace requests through structured JSON logs.
- **Observability:** Actuator folders contain pre-configured requests to `/actuator/prometheus` and `/actuator/health` on management ports.
