# CLAUDE.md — EatsNow Architecture & Development Guidelines

EatsNow is an event-driven food delivery orchestration platform (Software Architecture II, Case 6).

## Mandatory Policies
- **Language:** English only for all code, comments, docstrings, commit messages, and markdown artifacts.
- **Specification:** Strictly follow `docs/spec.md` (contracts, REST APIs, event schemas, state machine).

## Common Commands
- Build all services: `mvn clean package -DskipTests` (run inside `services/`)
- Build single service: `mvn clean package -DskipTests -pl common,<service> -am` (run inside `services/`)
- Run unit tests: `mvn test` (run inside `services/` or `services/<service>`)
- Start infrastructure: `docker compose up -d`
- Infrastructure smoke test: `./infra/smoke-test.sh`

## Anti-Duplication Rule: Use `services/common`
**DO NOT duplicate cross-cutting components.** All services reuse `com.eatsnow:common`:
- `Money`: `com.eatsnow.common.domain.model.Money` (COP in minor units)
- `PageResult<T>` & `PaginatedResponseDto<T>` (cursor pagination)
- `IdempotencyEntry` & `IdempotencyStoragePort` (`Idempotency-Key` on all mutating endpoints)
- `CorrelationIdFilter` & `CorrelationContext` (`X-Correlation-ID` header & MDC tracking)
- `ErrorResponseDto` & `GlobalExceptionHandler` (`code`, `message`, `correlationId`, `details`)
- `MessageEnvelope<T>`: `com.eatsnow.common.messaging.MessageEnvelope` (RabbitMQ events & commands)
- Common exceptions: `com.eatsnow.common.domain.exception.*`

Main classes must specify:
`@SpringBootApplication(scanBasePackages = {"com.eatsnow.<service>", "com.eatsnow.common"})`

## Architecture: Hexagonal (Ports & Adapters)
Services reside in `services/<name>/`:
- `domain/model/`: Pure records and entities (no framework dependencies).
- `domain/port/in/`: Use case interfaces (`GetRestaurantsQuery`, `CreateQuoteUseCase`, etc.).
- `domain/port/out/`: Outgoing interfaces (repositories, cache, outbox, messaging).
- `application/service/`: Orchestration and business logic implementing `port.in`.
- `infrastructure/adapter/in/web/`: Controllers, DTOs, request validation.
- `infrastructure/adapter/out/persistence/`: JPA entities in service schema, Spring Data repos.
- `infrastructure/adapter/out/messaging/`: RabbitMQ producers and consumers.
- `infrastructure/config/`: Spring configurations.

## Database & Observability Conventions
- **PostgreSQL:** 1 schema per service (`identity`, `catalog`, `orders`, `payments`, `restaurants`, `deliveries`, `notifications`). DB user `<schema>_svc`. Never query across schemas.
- **Flyway:** SQL migrations in `src/main/resources/db/migration/V1__...sql`.
- **Ports:** Application HTTP on `8080`, Spring Actuator / Prometheus on `8081` (`management.server.port=8081`).
- **Metrics:** Scraped from `/actuator/prometheus`. Do not add a `service` tag (Prometheus adds it).
- **Logging:** Structured JSON to stdout via `logback-spring.xml` containing `service: "<name>"`.
