# Feature: Redirect Cache with Cross-Replica Invalidation

## Decision

**Owner module**: `links` | **Event-coupled**: Y

Caches hash → target in a shared store. When a URL is deactivated or deleted, a
ShortUrlUpdatedEvent triggers cache invalidation across all replicas. `analytics`
consumes that same event and marks the per-hash stats inactive.

**Traps**: an in-process @Cacheable is not shared across replicas — clients get stale targets from the
non-invalidating replica; TTL-only eviction doesn’t satisfy the Level 4 bar.

**Technology hints**: Redis EXPIRE +pub/subinvalidation; Spring Cache with RedisCacheManager; event-driven invalidation subscriber.

---

- **Weight:** 21
- **Event-coupled:** Y 

---

## Later (after the 2 October agreement)

- **ADR:** [0006](../adr/0006-redis-redirect-cache.md)

### Acceptance criteria

- [x] Successful redirects use a hash-to-target cache; unknown and inactive hashes are never cached.
- [x] The Docker product profile stores redirect entries in shared Redis with a configurable TTL (`urlshortener.links.redirect-cache-ttl`).
- [x] Deactivation is persisted, idempotent, and available at `POST /api/link/{hash}/deactivate`; a configured `X-Admin-Token` is required.
- [x] A successful deactivation publishes `ShortUrlUpdatedEvent` after commit. Redis Pub/Sub invalidates the shared cache across replicas; local development uses an in-process cache and the same after-commit invalidation.
- [x] `analytics` consumes `ShortUrlUpdatedEvent`. After deactivation, `GET /api/stats/{hash}` keeps the click total and reports `active: false`.
- [x] `ShortUrlUpdatedEvent` is externalized to a durable RabbitMQ exchange. `links` and `analytics` each have their own durable queue. `ShortUrlUpdatedBrokerTests` covers that delivery.
- [x] Redirecting to a deactivated URL or deactivating an unknown hash returns 404. Missing/wrong admin tokens return 401.
- [x] On a two-replica Compose deployment, warm the redirect cache, deactivate through the load balancer, then verify redirects from both replicas return 404 and the Redis key is gone.

### Scale evidence

Compose runs two application replicas against one Redis cache. Runtime evidence collected on 2026-10-17:

1. Started the Compose product with a temporary `URLSHORTENER_ADMIN_TOKEN`; `db`, `redis`, `app1`, `app2`, and `lb` all became healthy.
2. Created a link through the load balancer and warmed it through both application replicas; both returned 307.
3. Redis reported a 3599-second TTL for the shared `urlshortener:redirect:<hash>` key.
4. Deactivated through the load balancer with `X-Admin-Token`; after invalidation, both direct replica requests returned 404, Redis `EXISTS` returned 0, and `GET /api/stats/{hash}` reported `active: false` with the click total kept.
5. Compose starts `app2` after `app1` is healthy so the two replicas do not initialize Spring Modulith's JDBC event schema at the same time. This startup brought both replicas up healthy.
6. `docker compose up` started RabbitMQ and both replicas with profiles `docker` and `broker`. RabbitMQ showed two durable queues, `urlshortener.links.redirect-invalidation` and `urlshortener.analytics.short-url-updated`, each with one consumer per replica. After deactivation both replicas returned 404 and stats reported `active: false`.
7. `docker compose --profile load run --rm k6` on that stack: 5 VUs, 30s, 640 iterations, 1280 checks, 0 failed, `http_req_failed` 0%. Exit code 0.

### Qualities

Fill **Assessed** with the grade you claim; **How to test** must falsify that number if it failed.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | `LinkDeactivationTests` and `ShortUrlUpdatedBrokerTests`: token enforcement, idempotence, cache invalidation, and `analytics` reporting `active: false` after the durable broker delivers `ShortUrlUpdatedEvent`. |
| **Scalability** | 3.5 | Two-replica Compose run, `docker compose up`, Redis Pub/Sub multi-replica invalidation, durable RabbitMQ exchange with separate queues, and k6 with 0 failed checks. |
| **Engineering** | 1.5 | `./gradlew check` passed; ADR 0004 is filed and `ModularityTests` passed. |

**Indicative total:** 10 / 10

### AI disclosure

- **Tools / skills:** Copilot SDK in VS Code; Cursor agent.
- **Used for:** implementation, tests, documentation, cache invalidation, and durable RabbitMQ externalization of `ShortUrlUpdatedEvent`.
- **Human-reviewed:** feature implementation and runtime Compose verification reviewed.
