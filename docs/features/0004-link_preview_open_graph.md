# Feature: Link Preview / Open Graph Metadata

## Decision (required by 2 October)

**Owner module**: `preview` | **Event-coupled**: Y (Escucha `ShortUrlCreatedEvent` y emite el nuevo evento LinkPreviewFetchedEvent a través del broker)

Fetches Open Graph title, image, and URL from the target asynchronously after creation. Exposes preview metadata on a separate endpoint (`GET /api/preview/{hash}`). Uses a circuit breaker for external fetches and externalizes processing via an external message broker (--profile broker) publishing the new domain event LinkPreviewFetchedEvent.

**Traps**: synchronous fetch on the redirect path adds latency; external sites may block HEAD/GET from server IPs; Open Graph parsing is fragile (Jsoup needs HTTPS + redirect following).

**Technology hints**: Jsoup; Resilience4j CircuitBreaker; listen to ShortUrlCreatedEvent -> async fetch via broker (--profile broker) -> publish LinkPreviewFetchedEvent -> store in preview module.

---

- **Weight:** 21
- **Event-coupled:** Y

---

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] `preview` listens to `ShortUrlCreatedEvent` and fetches Open Graph title, image and URL off the redirect path.
- [ ] `GET /api/preview/{hash}` returns that metadata once the fetch has finished. An unknown hash returns 404.
- [ ] The external fetch is protected by a circuit breaker. A failing target does not fail `GET /{hash}`.
- [ ] The module publishes a new `LinkPreviewFetchedEvent` through the broker (`--profile broker`). Reusing only `ShortUrlCreatedEvent` does not meet weight 21.

### Scale evidence

Not delivered in the 27 November prototype. The delivered Level 4 feature is [0006](0006-redirect_cache_cross_replica.md). The shared k6 run on 2026-10-17 had 0 failed checks.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 0 | No preview endpoint or `LinkPreviewFetchedEvent` exists yet. |
| **Scalability** | 0 | No preview-specific run. The product k6 run does not exercise this feature. |
| **Engineering** | 0 | No ADR for this feature. AI disclosure is present, but the feature is not implemented, so `./gradlew check` does not cover it. |

**Indicative total:** 0 / 10

### AI disclosure

- **Tools / skills:** Cursor agent.
- **Used for:** writing these acceptance criteria. The feature itself is not implemented.
- **Human-reviewed:** pending.