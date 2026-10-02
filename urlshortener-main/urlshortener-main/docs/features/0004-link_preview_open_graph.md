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

- [ ] …
- [ ] …

### Scale evidence

Fill this section with the evidence of the scalability of the feature.

### Qualities

Fill **Assessed** with the grade you claim; **How to test** must falsify that number if it failed.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 0 \| 5 | Acceptance tests for this feature |
| **Scalability** | 0–3.5 | Scale evidence above. Not `ModularityTests`. |
| **Engineering** | 0 \| 0.75 \| 1.5 | Hard gates: AI disclosure + `./gradlew check`. Then ADR + `ModularityTests`: both → **1.5**, one → **0.75**. |

**Indicative total:** _ / 10

### AI disclosure

- **Tools / skills:** …
- **Used for:** …
- **Human-reviewed:** …
- Or: **No AI assistance** was used for this feature.