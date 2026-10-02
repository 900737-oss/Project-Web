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
