# Feature: Redirect Cache with Cross-Replica Invalidation

## Decision

**Owner module**: `links` | **Event-coupled**: Y

Caches active hash → target entries in a shared store. Deactivation persists the
inactive state and triggers immediate cache invalidation across all replicas.
`analytics` consumes `ShortUrlUpdatedEvent` and marks the per-hash stats inactive.
A retained inactive row returns 410 Problem Detail; a missing or deleted row
returns 404 Problem Detail. Neither response is cached.

**Traps**: an in-process @Cacheable is not shared across replicas — clients get stale targets from the
non-invalidating replica; TTL-only eviction doesn’t satisfy the Level 4 bar.

**Technology hints**: Redis EXPIRE + Pub/Sub invalidation; Spring Cache with RedisCacheManager; event-driven invalidation subscriber.

---

- **Weight:** 21
- **Event-coupled:** Y 

---

## Later (after the 2 October agreement)

- **ADR:** [0006](../adr/0006-redis-redirect-cache.md)

### Course guidance (instructor)

The existing acceptance criteria remain applicable unless they contradict the
instructor's proposal. Returning 404 for a deactivated row that still exists was
contract drift: that response must be 410 Problem Detail.

Creation must be immediately visible after a previous miss. This does not add a
reactivation endpoint for inactive links.

The instructor's revised wording reviewed on 2026-11-22 preserves this contract
and explicitly requires a later active redirect to use the cached `Location`.
`RedirectCacheHitTests` verifies two HTTP 307 responses with the same target,
only one persistence lookup, and a click event for each successful redirect.

### Acceptance criteria

- [x] Successful redirects use a hash-to-target cache; unknown and inactive hashes are never cached.
- [x] The Docker product profile stores redirect entries in shared Redis with a configurable TTL (`urlshortener.links.redirect-cache-ttl`).
- [x] Deactivation is persisted, idempotent, and available at `POST /api/link/{hash}/deactivate`; a configured `X-Admin-Token` is required.
- [x] A successful state change publishes `ShortUrlUpdatedEvent` in the deactivation transaction. After commit, the originating replica deletes the shared Redis key before responding and sends Redis Pub/Sub invalidation; local development evicts its in-process entry after commit.
- [x] Repeating deactivation evicts the target before responding, including while the first transaction's after-commit eviction is still pending, without publishing a duplicate event.
- [x] `analytics` consumes `ShortUrlUpdatedEvent`. After deactivation, `GET /api/stats/{hash}` keeps the click total and reports `active: false`.
- [x] `ShortUrlUpdatedEvent` is externalized to a durable RabbitMQ exchange. `links` and `analytics` each have their own durable queue. `ShortUrlUpdatedBrokerTests` covers that delivery.
- [x] Deactivating an unknown hash returns 404. Missing/wrong admin tokens return 401.

The instructor's contract checks were verified on 2026-11-21:

#### Success

- [x] An unknown hash returns 404 `ProblemDetail` and is not remembered as missing.
- [x] Creating that hash afterwards makes the first `GET /{hash}` return 307 with `Location` equal to the target.
- [x] The first `GET /{hash}` for an active hash returns 307 with the target in `Location`.
- [x] A later `GET /{hash}` returns 307 with the cached target in `Location`, without another persistence lookup (`RedirectCacheHitTests`).

#### Rejection

- [x] After a successful deactivation response, the next `GET /{hash}` does not return the old 307 and does not wait for the cache TTL.
- [x] While the inactive row remains, `GET /{hash}` returns 410 `ProblemDetail`.
- [x] After that row is deleted, `GET /{hash}` returns 404 `ProblemDetail`.
- [x] A concurrent cache fill cannot restore a stale redirect after deactivation has completed.

#### Both replicas

- [x] Active 307 responses and the response after deactivation hold on either application replica and through the load balancer.
- [x] With RabbitMQ stopped, deactivation still invalidates the shared cache immediately and both replicas return 410, without waiting for a TTL or broker recovery.

### Verification commands

- `./gradlew check`: lint, static analysis, tests, coverage and module boundaries.
- `./gradlew test --tests '*LinkDeactivationTests' --tests '*RedirectCacheConcurrency*Tests' --tests '*ShortUrlUpdatedBrokerTests'`: HTTP contract, immediate creation after a miss, deletion, concurrent cache fills and broker delivery. PostgreSQL and broker tests require Docker.
- `docker compose up --build -d --wait`, then `powershell -ExecutionPolicy Bypass -File scripts/test-redirect-cache.ps1`: product acceptance against PostgreSQL, shared Redis, both app replicas and nginx; includes an outage of RabbitMQ and restores it afterwards.

The concurrency decision is recorded in ADR 0006: a cache miss holds a
`PESSIMISTIC_READ` lock on its row until its transaction ends, so a deactivation
cannot commit and invalidate the cache before an older fill has finished. Cache
hits still use Redis without a database lookup. TTL controls entry expiry; it is
not the mechanism that makes deactivation effective.

### Verification evidence (2026-11-21)

1. `gradlew.bat check bootJar` succeeded on the revised code: 81 tests, no skipped tests, failures or errors. JaCoCo reported 566 of 566 lines covered (100%), including 224 of 224 in `links`. All four modules have 100% line coverage. Branch coverage is a separate metric: 65 of 71 branches overall (91.55%).
2. The passing suite included `LinkDeactivationTests` (7), `RedirectCacheConcurrencyTests` (3 on HSQLDB), `RedirectCacheConcurrencyPostgresTests` (3), `RedirectCacheRedisTests` (5 with real Redis), `ShortUrlUpdatedBrokerTests` (1) and `ModularityTests` (2). Redis tests verify shared targets, TTL expiry, selective Pub/Sub invalidation, eviction after commit and preserved entries after rollback.
3. `docker compose up --build -d --wait` brought up the product with PostgreSQL, shared Redis, RabbitMQ, both app replicas and nginx. `scripts/test-redirect-cache.ps1` passed its complete acceptance run against that stack.
4. The script verified 404 Problem Detail for a miss, inserted the same hash, and verified the first and repeated 307 responses with the expected `Location` on both replicas and through the load balancer. Redis reported a remaining TTL of 3596 seconds.
5. After deactivation on `app1`, the first probe to `app2` returned 410 Problem Detail; both replicas and the load balancer rejected the old redirect. Deleting the inactive row changed subsequent responses to 404 Problem Detail. The full deactivation verification phase, including four HTTP probes and a SQL check, took 2.64 seconds, not the cache TTL.
6. With RabbitMQ stopped, the same deactivation verification passed in 2.60 seconds for the complete phase, with a remaining cache TTL of 3597 seconds before deactivation. The script restored RabbitMQ afterwards. These phase durations include the test commands and are not individual HTTP response latency measurements.

The complete acceptance run was repeated against the rebuilt final application
images, including the adjustment for overlapping idempotent deactivations. That
race is also covered by the passing HSQLDB and PostgreSQL concurrency tests.

Historical scale evidence from 2026-10-17: two replicas shared Redis with a
3599-second TTL and analytics retained click totals after deactivation. The k6
run completed 640 iterations and 1280 checks with 5 VUs over 30 seconds, zero
failed checks and `http_req_failed` 0%. Inactive rows then returned 404, which was
the contract drift; that historical run does not validate the corrected 410
response or the current broker-outage case.

### Revised wording verification (2026-11-22)

The newly supplied instructor criteria require no production-code changes: the
404/410 contract, immediate invalidation, creation after a previous miss, and
behavior across replicas during a broker outage match the implemented contract.
The older 404-for-inactive wording in the supplied proposal is superseded by its
instructor section.

`RedirectCacheHitTests` adds an explicit check that the later HTTP 307 obtains
its `Location` from the cache: two requests perform exactly one persistence
lookup and still publish two click events. `gradlew.bat test --rerun check bootJar`
passed with Docker available: 82 tests, zero skipped, failed or erroneous tests,
and 566 of 566 lines covered (100%). Both modularity checks and the Redis,
PostgreSQL and broker integrations passed. The two-replica Compose observations
above remain the runtime evidence collected on 2026-11-21.

### Qualities

These are claimed grades supported by the verification recorded above.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | `LinkDeactivationTests`, `RedirectCacheConcurrencyTests`, `RedirectCacheConcurrencyPostgresTests` and `ShortUrlUpdatedBrokerTests`: 404/410 Problem Detail, miss then creation, unchanged 307 targets, idempotence, concurrent fills, cache invalidation and analytics updates. |
| **Scalability** | 3.5 | `powershell -ExecutionPolicy Bypass -File scripts/test-redirect-cache.ps1` against the two-replica Compose product verifies both replicas and the load balancer, including immediate invalidation while RabbitMQ is stopped. Historical k6 evidence is listed above. |
| **Engineering** | 1.5 | The 2026-11-22 gate passed with 82 tests, none skipped, and 100% line coverage. ADR 0006 records the decision; `ModularityTests` passed both the module-boundary and hexagonal checks. |

**Indicative total:** 10 / 10

### AI disclosure

- **Tools / skills:** Copilot SDK in VS Code; Cursor agent; OpenAI Codex.
- **Used for:** implementation, tests, documentation, cache invalidation, durable RabbitMQ externalization of `ShortUrlUpdatedEvent`, the instructor's 404/410 and concurrency corrections, the coverage extension with real Redis transaction and Pub/Sub tests, and verification of the revised wording with a cached-Location regression test on 2026-11-22.
- **Human-reviewed:** the original implementation and historical Compose verification were reviewed; the revised contract and test results require review.
