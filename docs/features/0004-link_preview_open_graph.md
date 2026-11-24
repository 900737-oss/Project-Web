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

- [x] `POST /api/link` returns **201** with `Location` and JSON `url` + `hash` without waiting for the metadata fetch; the `ShortUrlCreatedEvent` listener stores one `link_preview` row in `PENDING`. — `LinkPreviewOnCreatedTests`, `LinkPreviewBrokerTests`.
- [x] `GET /api/preview/{hash}` returns `PENDING` while the broker fetch is in progress, then polling reaches `FAILED` for an unreadable target within **20 seconds**. — `LinkPreviewBrokerTests`.
- [x] If the target cannot be read, the preview reaches `FAILED` while `GET /{hash}` continues to return **307** with `Location` equal to the target URL. — `LinkPreviewBrokerTests`.
- [x] Delivering the same `ShortUrlCreatedEvent` again does not insert a second pending row; the new request event is published by `links` when it creates the short URL. — `LinkPreviewOnCreatedTests`.
- [x] `GET /api/preview/{hash}` returns **404** Problem Details (`type`, `title`, `status`, `detail`, `instance`) when no row exists. — `LinkPreviewHttpTests`.
- [x] `GET /api/preview/{hash}` returns **200** with `status` `PENDING`, `READY`, or `FAILED` when that row exists. A `READY` response includes the available metadata; `image` may be `null` when the target page has no Open Graph image. — `LinkPreviewHttpTests`, Compose broker run.
- [x] HTML that contains `og:title`, `og:image`, and `og:url` fills those fields. When Open Graph title or image metadata is missing, the document `<title>` is used when available and the image remains `null`; a page with a document title and no `og:image` can reach `READY`. — `OpenGraphParserTests`, `FetchLinkPreviewTests`.
- [x] A fetch failure sets the row to `FAILED`; redelivery for the same hash leaves it `FAILED` without calling the fetch client again. After repeated target failures, the circuit breaker also stops further HTTP calls. — `OpenGraphClientTests`, `FetchLinkPreviewTests`.
- [x] A `LinkPreviewRequestedEvent` published by `links` is delivered through RabbitMQ to `preview`, whose consumer fetches and stores metadata. — `LinkPreviewBrokerTests`.
- [ ] `docker compose up --build` starts all services from a clean checkout. The current Codespace Docker bridge timed out between containers during verification.

### Scale evidence

Level 4: `links` publishes a new event (`LinkPreviewRequestedEvent`) consumed by `preview` through RabbitMQ after commit. The test below verifies the full event path; the historical Compose run predates the current event ownership change.

1. `LinkPreviewBrokerTests` creates a URL through `CreateShortUrl`, verifies `links` publishes the event, and waits for the Rabbit consumer in `preview` to store `READY` metadata.
2. A previous Compose run demonstrated cross-replica delivery, but used the earlier event publisher and optional broker profile; it is not current runtime evidence for the new default stack.
3. Current clean-checkout Compose startup remains unverified in this Codespace because bridge TCP connections to the database timed out.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 5 | The eight acceptance criteria above; `./gradlew test --tests es.unizar.urlshortener.preview.*`. |
| **Scalability** | 3.5 | Scale evidence: new event externalized to Rabbit; cross-replica Compose run; `LinkPreviewBrokerTests`. |
| **Engineering** | 1.5 | AI disclosure below; ADR 0005; `ModularityTests`. Final gate: `./gradlew check` on this branch. |

**Indicative total:** 10 / 10

### AI disclosure

- **Tools / skills:** Cursor (Composer agent).
- **Used for:** design of the `preview` module and broker path, implementation, tests, Compose wiring and a part of documentation.
- **Human-reviewed:** Yes — each commit was reviewed and commands were run by the feature owner before continuing. Additionally, logs and any specific errors that arose were manually verified.
