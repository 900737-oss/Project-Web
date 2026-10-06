# Feature: Redirect Cache with Cross-Replica Invalidation

## Decision

**Owner module**: `links` | **Event-coupled**: Y

Caches hash → target in a shared store. When a URL is deactivated or deleted, a
ShortUrlUpdatedEvent triggers cache invalidation across all replicas.

**Traps**: an in-process @Cacheable is not shared across replicas — clients get stale targets from the
non-invalidating replica; TTL-only eviction doesn’t satisfy the Level 4 bar.

**Technology hints**: Redis EXPIRE +pub/subinvalidation; Spring Cache with RedisCacheManager; event-driven invalidation subscriber.

---

- **Weight:** 21
- **Event-coupled:** Y 

---

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [x] Successful redirects use a hash-to-target cache; unknown and inactive hashes are never cached.
- [x] The Docker product profile stores redirect entries in shared Redis with a configurable TTL (`urlshortener.links.redirect-cache-ttl`).
- [x] Deactivation is persisted, idempotent, and available at `POST /api/link/{hash}/deactivate`; a configured `X-Admin-Token` is required.
- [x] A successful deactivation publishes `ShortUrlUpdatedEvent` after commit. Redis Pub/Sub invalidates the shared cache across replicas; local development uses an in-process cache and the same after-commit invalidation.
- [x] Redirecting to a deactivated URL or deactivating an unknown hash returns 404. Missing/wrong admin tokens return 401.
- [x] On a two-replica Compose deployment, warm the redirect cache, deactivate through the load balancer, then verify redirects from both replicas return 404 and the Redis key is gone.

### Scale evidence

Compose runs two application replicas against one Redis cache. Runtime evidence collected on 2026-10-03:

1. Started the Compose product with a temporary `URLSHORTENER_ADMIN_TOKEN`; `db`, `redis`, `app1`, `app2`, and `lb` all became healthy.
2. Created a link through the load balancer and warmed it through both application replicas; both returned 307.
3. Redis reported a 3599-second TTL for the shared `urlshortener:redirect:<hash>` key.
4. Deactivated through the load balancer with `X-Admin-Token`; after invalidation, both direct replica requests returned 404 and Redis `EXISTS` returned 0 for the key.
5. The first parallel app startup exposed a race initializing Spring Modulith's JDBC event schema. Compose now starts `app2` after `app1` is healthy; a clean startup then brought both replicas up healthy.

### Qualities

Fill **Assessed** with the grade you claim; **How to test** must falsify that number if it failed.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | `LinkDeactivationTests`: token enforcement, idempotence, missing-link handling, and invalidation after a cached redirect. |
| **Scalability** | 2 | Two-replica Compose run: verify healthy instances, Redis TTL, and 404 from both replicas after deactivation. |
| **Engineering** | 1.5 | `./gradlew check` passed; ADR 0004 is filed and `ModularityTests` passed. |

**Indicative total:** 8.5 / 10

### AI disclosure

- **Tools / skills:** Copilot SDK in VS Code.
- **Used for:** implementation, tests, and documentation.
- **Human-reviewed:** feature implementation and runtime Compose verification reviewed.
