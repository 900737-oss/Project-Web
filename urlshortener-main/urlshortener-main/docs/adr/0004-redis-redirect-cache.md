# ADR 0004: Shared Redis redirect cache and invalidation

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Team / feature card [0006](../features/0006-redirect_cache_cross_replica.md)

## Context

Each application replica serves `GET /{hash}`. An in-process cache can keep returning an outdated target after a link is deactivated on another replica. Redirect lookups are owned by `links`; deactivation must also persist so that a cache miss cannot make the link active again.

## Decision

1. Add an `active` state to `ShortUrl`; deactivation is persisted and idempotent. Redirect lookups exclude inactive records.
2. Keep cache access behind the `RedirectCache` secondary port. In Docker, store hash-to-target entries in Redis with an expiry. Outside Docker, use a process-local implementation so the existing HSQLDB development/test workflow needs no Redis service.
3. Publish `ShortUrlUpdatedEvent` only when deactivation changes state. Publish it in the service transaction and handle it after commit.
4. In Docker, relay the committed event hash over Redis Pub/Sub. Every replica subscribes and evicts the shared Redis key. Local development evicts directly after commit.
5. Expose `POST /api/link/{hash}/deactivate`. Require a non-empty, configured `X-Admin-Token`, compare tokens in constant time, and fail closed with 401 when absent or incorrect. Configure the token through `URLSHORTENER_ADMIN_TOKEN`; do not commit it.

## Consequences

- All replicas read the same cached value, and a committed deactivation invalidates it through the shared Pub/Sub channel.
- Cache entries expire after the configured TTL even if a future invalidation is missed.
- Redis and a shared admin token are required for the Docker product deployment. The local profile remains self-contained but does not demonstrate cross-replica behavior.
- Redis Pub/Sub is transient. If a subscriber is unavailable during publication, TTL bounds stale cache retention; a subsequent cache miss checks the persisted inactive state.
- Compose starts `app2` only after `app1` is healthy to avoid concurrent initialization of Spring Modulith's shared JDBC event-publication schema.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| In-process cache only | Replicas can continue serving stale targets and do not share cache entries. |
| TTL-only invalidation | Does not promptly invalidate deactivated links and misses the feature's cross-replica requirement. |
| Redis keyspace notifications | Requires server-side notification configuration and couples invalidation to Redis key-event settings. |
| Spring Cache abstraction | Adds indirection without benefit for the single string-valued redirect entry; direct Redis operations make key TTL and Pub/Sub behavior explicit. |
| External message broker | Existing broker profile is only a stub; Redis is already required for the shared cache and supports the invalidation channel. |

## Evidence

- `LinkDeactivationTests`: token enforcement, repeat deactivation, missing hashes, and invalidation after warming a redirect cache.
- Compose runtime verification: both app replicas healthy; both returned 307 before deactivation; Redis TTL was 3599 seconds; after deactivation, both returned 404 and the Redis key was absent.
- Parallel startup initially reproduced a race in the Spring Modulith JDBC schema initializer. Serializing the app startup in Compose resolved it; a clean two-replica startup was verified.
