# EatsNow — Gemini / Antigravity Agent Guidelines

This repository implements the **EatsNow** delivery and order orchestration platform (Case 6).

## Mandatory Rules
- **English-Only Policy:** All code, docstrings, comments, commits, and technical documentation must be written in English.
- **Contract-First:** All implementations must conform strictly to `docs/spec.md` and `docs/adrs/`.

## Anti-Duplication Rule: Reuse `services/common`
The `services/common` library (`com.eatsnow:common`) contains all platform-wide cross-cutting contracts. **Never** reinvent or duplicate:
- `Money`: `com.eatsnow.common.domain.model.Money` (`amountMinor`, `currency = "COP"`)
- `PageResult<T>` & `PaginatedResponseDto<T>`
- `IdempotencyEntry` & `IdempotencyStoragePort` (required on all mutating endpoints via `Idempotency-Key: UUID`)
- `CorrelationIdFilter` & `CorrelationContext` (`X-Correlation-ID: UUID` on all requests/responses and MDC logs)
- `ErrorResponseDto` & `GlobalExceptionHandler` (`code`, `message`, `correlationId`, `details`)
- `MessageEnvelope<T>`: `com.eatsnow.common.messaging.MessageEnvelope` (RabbitMQ topic exchanges `eatsnow.events.v1` and `eatsnow.commands.v1`)
- Standard domain exceptions: `com.eatsnow.common.domain.exception.*`

New services must declare `com.eatsnow:common` in their `pom.xml` and include `com.eatsnow.common` in `@SpringBootApplication(scanBasePackages = {"com.eatsnow.<service>", "com.eatsnow.common"})`.

## Architecture: Hexagonal (Ports & Adapters)
Every service in `services/<service-name>` follows:
- `domain/model/`: Domain entities and value objects.
- `domain/port/in/`: Query and Command use case interfaces.
- `domain/port/out/`: Repository, cache, outbox, and messaging interfaces.
- `application/service/`: Use case implementations with core business logic.
- `infrastructure/adapter/in/web/`: RestControllers and DTOs.
- `infrastructure/adapter/out/persistence/`: JPA entities in the service schema and Spring Data repos.
- `infrastructure/adapter/out/messaging/`: RabbitMQ event/command producers and consumers.
- `infrastructure/config/`: Spring bean configurations.

## Infrastructure & Observability Standards
- **PostgreSQL 16:** Dedicated schema and role per service (`<schema>_svc` / `<schema>_dev_pw`). Cross-schema joins/queries are forbidden. Schema tables are created via Flyway migrations (`db/migration/V1__...sql`).
- **Observability:**
  - Application HTTP port: `8080`.
  - Management Actuator port: `8081` (`management.server.port=8081`), Prometheus endpoint `/actuator/prometheus`.
  - Do not set `service` tag on metrics: Prometheus adds it from Docker DNS discovery.
  - Logs: Structured JSON to `stdout` containing `timestamp, level, service, operation, outcome, traceId, spanId` (Spec 6.2).

## Build & Test Commands
- Build all services: `mvn clean package -DskipTests` (in `services/`)
- Build single service: `mvn clean package -DskipTests -pl common,<service> -am` (in `services/`)
- Run tests: `mvn test`
