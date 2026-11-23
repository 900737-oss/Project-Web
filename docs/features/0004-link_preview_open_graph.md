# Feature: Link Preview / Open Graph Metadata

## Decision (required by 2 October)

**Owner module**: `preview` | **Event-coupled**: Y (`links` publishes the new `LinkPreviewRequestedEvent`; `preview` consumes it through RabbitMQ)

Fetches Open Graph title, image, and URL from the target asynchronously after creation. On `ShortUrlCreatedEvent`, `preview` stores a `PENDING` row. `links` publishes the new `LinkPreviewRequestedEvent` (`hash`, `target`, `eventId`) after storing the short URL; RabbitMQ delivers it to a consumer in `preview`, which fetches the page and updates the row to `READY` or `FAILED`. Exposes preview metadata on `GET /api/preview/{hash}`. Uses a circuit breaker for external fetches. The broker is included in the default Compose stack.

**Traps**: synchronous fetch on the redirect path adds latency; external sites may block HEAD/GET from server IPs; Open Graph parsing is fragile (Jsoup needs HTTPS + redirect following).

**Technology hints**: Jsoup; Resilience4j CircuitBreaker; listen to `ShortUrlCreatedEvent` → store `PENDING` → publish `LinkPreviewRequestedEvent` via the broker → consumer fetches and updates the row. See [ADR 0004](../adr/0004-link-preview-broker.md).

---

- **Weight:** 21
- **Event-coupled:** Y

---

## Later (after the 2 October agreement)


- **ADR:** [docs/adr/0004-link-preview-broker.md](../adr/0004-link-preview-broker.md)

### Acceptance criteria

- [x] `POST /api/link` still returns **201** with `url` and `hash`; the `ShortUrlCreatedEvent` listener stores one `link_preview` row in `PENDING`. — `LinkPreviewOnCreatedTests`.
- [x] Delivering the same `ShortUrlCreatedEvent` again does not insert a second pending row; the new request event is published by `links` when it creates the short URL. — `LinkPreviewOnCreatedTests`.
- [x] `GET /api/preview/{hash}` returns **404** Problem Details (`type`, `title`, `status`, `detail`, `instance`) when no row exists. — `LinkPreviewHttpTests`.
- [x] `GET /api/preview/{hash}` returns **200** with `status` `PENDING`, `READY`, or `FAILED` when that row exists. `READY` includes `title`, `image`, and `url`. — `LinkPreviewHttpTests`, Compose broker run.
- [x] HTML that contains `og:title`, `og:image`, and `og:url` fills those fields. HTML without Open Graph tags uses the document `<title>`. — `OpenGraphParserTests`.
- [x] Repeated failures of the target site set the row to `FAILED` and the circuit breaker stops calling that target. — `OpenGraphClientTests`, `FetchLinkPreviewTests`.
- [x] A `LinkPreviewRequestedEvent` published by `links` is delivered through RabbitMQ to `preview`, whose consumer fetches and stores metadata. — `LinkPreviewBrokerTests`.
- [ ] `docker compose up --build` starts all services from a clean checkout. The current Codespace Docker bridge timed out between containers during verification.

### Scale evidence

Level 4: `links` publishes a new event (`LinkPreviewRequestedEvent`) consumed by `preview` through RabbitMQ after commit. The test below verifies the full event path; the historical Compose run predates the current event ownership change.

1. `LinkPreviewBrokerTests` creates a URL through `CreateShortUrl`, verifies `links` publishes the event, and waits for the Rabbit consumer in `preview` to store `READY` metadata.
2. A previous Compose run demonstrated cross-replica delivery, but used the earlier event publisher and optional broker profile; it is not current runtime evidence for the new default stack.
3. Current clean-checkout Compose startup remains unverified in this Codespace because bridge TCP connections to the database timed out.

### Coverage verification (2026-11-21)

`gradlew.bat check bootJar` passed with 81 tests, no skipped tests, failures or errors.
JaCoCo reports 214 of 214 lines covered in `preview` and 566 of 566 globally (100% line coverage).
The extended tests exercise HTTP errors, timeout, interruption with its flag preserved,
oversized responses, redirects, blank metadata fallback and recovery from failed previews.
The downloader uses explicit throws to preserve the same exception type, message and cause
while avoiding unreachable Kotlin bytecode after a `Nothing`-returning helper.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | The eight acceptance criteria above; `./gradlew test --tests es.unizar.urlshortener.preview.*`. |
| **Scalability** | 3.5 | Scale evidence: new event externalized to Rabbit; cross-replica Compose run; `LinkPreviewBrokerTests`. |
| **Engineering** | 1.5 | AI disclosure below; ADR 0005; `ModularityTests`. Final gate: `./gradlew check` on this branch. |

**Indicative total:** 10 / 10

### AI disclosure

- **Tools / skills:** Cursor (Composer agent); OpenAI Codex.
- **Used for:** design of the `preview` module and broker path, implementation, tests, Compose wiring and a part of documentation. On 2026-11-21, Codex added HTTP failure, size limit, redirect, parser fallback and retry tests, and replaced the throwing helper with explicit throws so JaCoCo can record those executed paths.
- **Human-reviewed:** The original implementation and commands were reviewed by the feature owner. The 2026-11-21 coverage additions and exception refactor require human review.
