# catalog-baseline

Feature 2 baseline: 50 concurrent catalog readers (think time 0.1 s), read-only, for 60s.

- Run (UTC): 2026-11-11T00:16:50.911Z
- Iterations: 27825
- Dropped iterations: 0

| Request | Count | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) | Error rate | Target p95 | Result |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| GET /restaurants | 27828 | 2.8 | 5.3 | 10.2 | 35.0 | 0.00% | ≤ 300 ms, < 1% errors | PASS |
| GET /restaurants/{id}/menu | 27828 | 3.5 | 6.5 | 12.0 | 38.9 | 0.00% | ≤ 300 ms, < 1% errors | PASS |

**Overall: PASS**
