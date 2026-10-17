# Feature: Link Preview / Open Graph Metadata

## Decision (required by 2 October)

**Owner module**: `preview` | **Event-coupled**: Y (Listens to `ShortUrlCreatedEvent` and publishes the new event `LinkPreviewRequestedEvent` through the broker)

Fetches Open Graph title, image, and URL from the target asynchronously after creation. On `ShortUrlCreatedEvent`, `preview` stores a `PENDING` row and publishes `LinkPreviewRequestedEvent` (`hash`, `target`, `eventId`). A RabbitMQ consumer fetches the page and updates that row to `READY` or `FAILED`. Exposes preview metadata on `GET /api/preview/{hash}`. Uses a circuit breaker for external fetches. The broker path is `docker compose --profile broker`.

**Traps**: synchronous fetch on the redirect path adds latency; external sites may block HEAD/GET from server IPs; Open Graph parsing is fragile (Jsoup needs HTTPS + redirect following).

**Technology hints**: Jsoup; Resilience4j CircuitBreaker; listen to `ShortUrlCreatedEvent` → store `PENDING` → publish `LinkPreviewRequestedEvent` via the broker → consumer fetches and updates the row. See [ADR 0005](../adr/0005-link-preview-broker.md).

---

- **Weight:** 21
- **Event-coupled:** Y

---

## Later (after the 2 October agreement)

- **ADR:** [docs/adr/0005-link-preview-broker.md](../adr/0005-link-preview-broker.md)

### Acceptance criteria

- [ ] `POST /api/link` still returns **201** with `url` and `hash`, and leaves one `link_preview` row in `PENDING` for that hash.
- [ ] Delivering the same `ShortUrlCreatedEvent` again does not insert a second row and does not publish a second `LinkPreviewRequestedEvent`.
- [ ] `GET /api/preview/{hash}` returns **404** Problem Details (`type`, `title`, `status`, `detail`, `instance`) when no row exists.
- [ ] `GET /api/preview/{hash}` returns **200** with `status` `PENDING`, `READY`, or `FAILED` when that row exists. `READY` includes `title`, `image`, and `url`.
- [ ] HTML that contains `og:title`, `og:image`, and `og:url` fills those fields. HTML without Open Graph tags uses the document `<title>`.
- [ ] Repeated failures of the target site set the row to `FAILED` and the circuit breaker stops calling that target.
- [ ] A `LinkPreviewRequestedEvent` that arrives through RabbitMQ, and was not created in this JVM (app1 or app2, the replicas), moves the matching `PENDING` row to `READY`.
- [ ] With RabbitMQ down, `POST /api/link` still returns **201**.

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
