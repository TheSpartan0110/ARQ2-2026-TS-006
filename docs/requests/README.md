# EatsNow — Postman API Collections

This directory contains ready-to-import Postman collections for testing EatsNow microservices independently and via the API Gateway.

---

## 1. Files in this Directory

| File | Target Service | Description |
|---|---|---|
| [`identity.postman_collection.json`](identity.postman_collection.json) | Identity & Access Service | OIDC discovery and JWKS, self-profile (`/profiles/me`), and admin profile management (`/admin/profiles`), authenticated with OAuth 2.0 + PKCE. |
| [`catalog.postman_collection.json`](catalog.postman_collection.json) | Catalog Service | Restaurant listings, menu retrieval, and checkout quote generation (`/quotes`) with signed tokens. |
| [`eatsnow_local.postman_environment.json`](eatsnow_local.postman_environment.json) | All services | Local environment variables (`baseUrl`, ports, test UUIDs). |

---

## 2. Default Ports & Addresses (Docker Compose)

| Service | Application Port (HTTP) | Management Port (Prometheus / Health) |
|---|---|---|
| **API Gateway** (single entry point, OIDC issuer) | `http://localhost:8090` | — |
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

### Step 2: Log In with OAuth 2.0 (Authorization Code + PKCE)
Identity is an OIDC provider (Spring Authorization Server, ADR 005); there is no login API call. The collections call the API Gateway (`http://localhost:8090`), which is also the token issuer.
1. Open the folder (Identity) or the collection (Catalog) and go to its **Authorization** tab. It is preconfigured with the confidential client `eatsnow-cli`, PKCE (S256) and the redirect URI `https://oauth.pstmn.io/v1/callback`.
2. Click **Get New Access Token**. Postman opens the EatsNow Identity login page: sign in with the account named in the token name (`alex@example.test` for customer requests, `admin@eatsnow.test` for admin requests; password `password123`).
3. Click **Use Token**. Requests in that folder inherit it. Tokens last 15 minutes; Postman can renew them with the rotating refresh token.
4. To switch accounts, clear Postman's cookies for `localhost` (the Identity login session) before requesting a new token.

The folder **Authentication & OIDC** contains the public discovery document and the JWKS. Restaurant and driver accounts created by an admin receive an activation email in Mailpit (`http://localhost:8025`) to set their password.

---

## 4. Built-in Standards

- **Idempotency:** Mutating endpoints (`POST`, `PATCH`) automatically generate a fresh UUID using Postman's dynamic variable `{{$guid}}` for the `Idempotency-Key` header.
- **Traceability:** Requests send `X-Correlation-ID: {{$guid}}` to trace requests through structured JSON logs.
- **Observability:** Actuator folders contain pre-configured requests to `/actuator/prometheus` and `/actuator/health` on management ports.
