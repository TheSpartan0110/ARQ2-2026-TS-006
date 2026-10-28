# EatsNow — AI Coding Agent Guidelines

This repository contains the **EatsNow** delivery and order orchestration platform (Software Architecture II, Case 6).

> **MANDATORY POLICY:** All repository deliverables, code, Java docstrings/comments, commit messages, and documentation must be written in **English only**.

---

## 1. System Architecture & Context

- **Style:** Event-driven microservices with durable messaging (RabbitMQ), cache-aside (Redis), and isolated persistence (PostgreSQL).
- **Service internal architecture:** **Hexagonal Architecture (Ports and Adapters)** for all business microservices located under `services/<service-name>`.
- **Single Source of Truth for Contracts:** [`docs/spec.md`](docs/spec.md) and [`docs/adrs/`](docs/adrs/). Never invent or deviate from the REST endpoints, message schemas, or state transitions defined there.

---

## 2. CRITICAL: Anti-Duplication Rule (`services/common`)

**BEFORE writing any new domain class, utility, DTO, or filter, CHECK `services/common`!**
The `services/common` library (`com.eatsnow:common`) contains all platform-wide cross-cutting contracts and models. **DO NOT reimplement or duplicate these classes in individual services**:

| Component | Fully Qualified Name | Purpose |
|---|---|---|
| `Money` | `com.eatsnow.common.domain.model.Money` | Currency in minor units (`amountMinor`, `currency = "COP"`). |
| `Role` | `com.eatsnow.common.domain.model.Role` | System roles (`USER`, `RESTAURANT`, `DRIVER`, `ADMIN`). |
| `PageResult<T>` | `com.eatsnow.common.domain.model.PageResult` | Domain container for cursor-based pagination. |
| `PaginatedResponseDto<T>` | `com.eatsnow.common.web.dto.PaginatedResponseDto` | Web response envelope for `items` and `nextCursor`. |
| `IdempotencyEntry` | `com.eatsnow.common.domain.model.IdempotencyEntry` | Model for tracking request hashes and responses. |
| `IdempotencyStoragePort` | `com.eatsnow.common.domain.port.out.IdempotencyStoragePort` | Port for idempotency persistence across mutating endpoints. |
| `CorrelationIdFilter` | `com.eatsnow.common.web.filter.CorrelationIdFilter` | HTTP filter enforcing and propagating `X-Correlation-ID`. |
| `CorrelationContext` | `com.eatsnow.common.web.filter.CorrelationContext` | Helper to read current `correlationId` from MDC. |
| `ErrorResponseDto` | `com.eatsnow.common.web.dto.ErrorResponseDto` | Standard error JSON schema `{ "code", "message", "correlationId", "details" }`. |
| `GlobalExceptionHandler` | `com.eatsnow.common.web.GlobalExceptionHandler` | Spring `@RestControllerAdvice` handling domain and web errors. |
| `DomainException` + subtypes | `com.eatsnow.common.domain.exception.*` | `EntityNotFoundException` (404), `InvalidRequestException` (400), `ItemUnavailableException` (409), `IdempotencyConflictException` (409), `RequestInProgressException` (409). |
| `MessageEnvelope<T>` | `com.eatsnow.common.messaging.MessageEnvelope` | Common RabbitMQ envelope carrying `messageId`, `correlationId`, `traceparent`, `causationId`. |

Every new service POM must include:
```xml
<dependency>
    <groupId>com.eatsnow</groupId>
    <artifactId>common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```
And its Spring Boot main class must scan both packages:
```java
@SpringBootApplication(scanBasePackages = {"com.eatsnow.<service>", "com.eatsnow.common"})
```

---

## 3. Microservice Internal Structure (Hexagonal)

All services in `services/<name>` must follow this exact layout:
```text
services/<name>/
├── Dockerfile
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/eatsnow/<name>/
    │   │   ├── <Name>Application.java
    │   │   ├── domain/
    │   │   │   ├── model/                      # Service-specific domain entities/records
    │   │   │   ├── port/in/                    # Use case query and command interfaces
    │   │   │   └── port/out/                   # Repository, cache, outbox, and messaging ports
    │   │   ├── application/service/            # Use case implementations (pure business rules)
    │   │   └── infrastructure/
    │   │       ├── adapter/in/web/             # RestControllers and service-specific DTOs
    │   │       ├── adapter/out/persistence/    # JPA entities, Spring Data repos, and adapters
    │   │       ├── adapter/out/messaging/      # RabbitMQ producers and consumers (Spec 4.1-4.3)
    │   │       └── config/                     # Spring configurations (Redis, Security, etc.)
    │   └── resources/
    │       ├── application.yml                 # Server port 8080, management port 8081
    │       ├── logback-spring.xml              # JSON logging to stdout with "service": "<name>"
    │       └── db/migration/V1__...sql         # Flyway migrations
    └── test/
```

---

## 4. Platform Rules & Infrastructure Constraints

1. **Database Isolation (ADR 004):**
   - PostgreSQL schema per service (`identity`, `catalog`, `orders`, `payments`, `restaurants`, `deliveries`, `notifications`).
   - Connect with user `<schema>_svc` and password `${<SCHEMA>_DB_PASSWORD:<schema>_dev_pw}`.
   - **Never** perform cross-schema queries. Data sharing happens exclusively through asynchronous messaging.
2. **Observability & Ports (Spec 6.1 & 6.4):**
   - Application port: `8080` (HTTP).
   - Management port: `8081` (`management.server.port=8081`) exposing Prometheus metrics at `/actuator/prometheus`.
   - **Do NOT** add a `service` tag to metrics; Prometheus DNS scrape config adds it automatically.
   - All logs must be written as structured JSON to `stdout` containing at least `timestamp, level, service, operation, outcome, traceId, spanId`. OpenTelemetry Collector drops any line without the `service` attribute.
3. **Idempotency (Spec 3.1):**
   - All mutating endpoints (`POST`, `PATCH`) require `Idempotency-Key: UUID`.
4. **Correlation (Spec 3.1 & 6.2):**
   - `X-Correlation-ID: UUID` must be received or generated, returned in every HTTP response, and propagated through all message envelopes.

---

## 5. Development & Build Commands

- **Build everything:** `mvn clean package -DskipTests` (inside `services/`).
- **Build single service:** `mvn clean package -DskipTests -pl common,<service-name> -am` (inside `services/`).
- **Run tests:** `mvn test` (inside `services/` or `services/<service-name>`).
- **Start base infrastructure:** `docker compose up -d`
- **Verify infrastructure:** `./infra/smoke-test.sh`
