# ADR 002 — Catalog Cache

- **Status:** Proposed
- **Category:** Performance and Availability

## Context and Problem

The restaurant catalog and menus receive frequent queries. If every query accesses the primary database directly, read traffic may affect performance and compete with transactional purchasing operations.

## Considered Alternatives

1. **Direct PostgreSQL Queries:** A simple architecture that returns current database values on each read, but increases database load.
2. **Redis:** An in-memory cache with low access latency and configurable key expiration.
3. **Local Cache per Instance:** Reduces latency, but may create inconsistencies between replicas and requires distributed cache invalidation management.

## Decision

Implement Redis as the cache for the Catalog Service using the cache-aside strategy. The service will first query Redis. On a cache miss, it will query PostgreSQL and store the result for a defined period.

Relevant changes to prices, menus, or availability will invalidate or update the corresponding cache keys. PostgreSQL will remain the source of truth for the catalog, while Redis will be used exclusively to optimize queries.

If Redis is unavailable, the Catalog Service will fall back to PostgreSQL. Cache failures must not prevent catalog queries from being served, provided the primary database remains available.

## Trade-offs

### Positives (What We Gain)

- Reduces latency for frequently requested catalog data.
- Reduces read load on PostgreSQL.
- Helps isolate catalog queries from order-processing workloads.
- Allows expiration periods to be configured according to the nature of the data.

### Negatives (Risks / What We Lose)

- The cache may return stale data if invalidation fails.
- Redis introduces additional infrastructure, memory consumption, and monitoring requirements.
- Cache loss or unavailability may cause a sudden increase in PostgreSQL queries.

These risks will be mitigated through TTL settings, cache-key invalidation, appropriate load controls, monitoring, and fallback reads from PostgreSQL when Redis is unavailable.

## Final Justification

Redis improves the performance of a frequently accessed functionality without requiring catalog queries to share the same processing path as purchasing operations. PostgreSQL remains the source of truth, while Redis serves as a performance optimization rather than the authoritative data store.
