# GitHub Copilot Instructions for EatsNow

This repository contains the **EatsNow** delivery and order orchestration platform (Case 6).

## Core Principles
1. **English-Only Policy:** All code, comments, docstrings, commit messages, and PR descriptions must be written in English.
2. **Contract-First:** Refer to `docs/spec.md` and `docs/adrs/` for all REST endpoints, schemas, and messaging contracts.
3. **Hexagonal Architecture:** Every service in `services/<service-name>` must follow Hexagonal Architecture (domain, application, infrastructure).

## Strict Anti-Duplication Rule: Use `services/common`
Never create duplicate classes for cross-cutting concepts. Always import from `com.eatsnow.common.*`:
- **Domain Value Objects:** `com.eatsnow.common.domain.model.Money`, `com.eatsnow.common.domain.model.PageResult`, `com.eatsnow.common.domain.model.IdempotencyEntry`
- **Exceptions:** `com.eatsnow.common.domain.exception.DomainException`, `EntityNotFoundException`, `InvalidRequestException`, `ItemUnavailableException`, `IdempotencyConflictException`, `RequestInProgressException`
- **Web Infrastructure:** `com.eatsnow.common.web.filter.CorrelationIdFilter` (`X-Correlation-ID`), `com.eatsnow.common.web.GlobalExceptionHandler`, `com.eatsnow.common.web.dto.ErrorResponseDto`, `com.eatsnow.common.web.dto.PaginatedResponseDto`
- **Messaging:** `com.eatsnow.common.messaging.MessageEnvelope<T>` for all RabbitMQ events and commands.

Every new service must:
1. Add `<dependency><groupId>com.eatsnow</groupId><artifactId>common</artifactId><version>1.0.0-SNAPSHOT</version></dependency>` in its `pom.xml`.
2. Include `scanBasePackages = {"com.eatsnow.<service>", "com.eatsnow.common"}` in its `@SpringBootApplication`.

## Technical & Infrastructure Conventions
- **Build tool:** Apache Maven (JDK 17). Build with `mvn clean package -DskipTests` inside `services/`.
- **Database:** PostgreSQL 16 schema-per-service (ADR 004). Username `<schema>_svc`. Never perform cross-schema queries. Tables are managed via Flyway in `src/main/resources/db/migration/`.
- **Ports:** App on port `8080`, Spring Actuator / Prometheus on management port `8081` (`management.server.port=8081`).
- **Telemetry:** Structured JSON logging to stdout with `"service": "<name>"` (Spec 6.2).
