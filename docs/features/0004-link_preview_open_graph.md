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

- [x] `POST /api/link` still returns **201** with `url` and `hash`, and leaves one `link_preview` row in `PENDING` for that hash. — `LinkPreviewOnCreatedTests`, Compose without Rabbit.
- [x] Delivering the same `ShortUrlCreatedEvent` again does not insert a second row and does not publish a second `LinkPreviewRequestedEvent`. — `LinkPreviewOnCreatedTests`.
- [x] `GET /api/preview/{hash}` returns **404** Problem Details (`type`, `title`, `status`, `detail`, `instance`) when no row exists. — `LinkPreviewHttpTests`.
- [x] `GET /api/preview/{hash}` returns **200** with `status` `PENDING`, `READY`, or `FAILED` when that row exists. `READY` includes `title`, `image`, and `url`. — `LinkPreviewHttpTests`, Compose broker run.
- [x] HTML that contains `og:title`, `og:image`, and `og:url` fills those fields. HTML without Open Graph tags uses the document `<title>`. — `OpenGraphParserTests`.
- [x] Repeated failures of the target site set the row to `FAILED` and the circuit breaker stops calling that target. — `OpenGraphClientTests`, `FetchLinkPreviewTests`.
- [x] A `LinkPreviewRequestedEvent` that arrives through RabbitMQ, and was not created in this JVM (app1 or app2, the replicas), moves the matching `PENDING` row to `READY`. — `LinkPreviewBrokerTests`; Compose with broker: `app1` pending, `app2` ready for hash `1003`.
- [x] With RabbitMQ down, `POST /api/link` still returns **201**. — `docker compose up` (no rabbit service); hash `1000` stayed `PENDING`.

### Scale evidence

Level 4: a new event (`LinkPreviewRequestedEvent`) is externalized to RabbitMQ after commit. Evidence collected on 2026-10-18:

1. `docker compose up --build` started `db`, `redis`, `app1`, `app2`, and `lb` healthy **without** Rabbit. Both apps used only the `docker` profile. `POST /api/link` returned **201** (`hash` `1000`); `GET /api/preview/1000` stayed `PENDING`.
2. `docker compose --profile broker up --build` started Rabbit and both apps with profiles `docker,broker`. Apps connected to `rabbit:5672` as user `urlshortener`.
3. `LinkPreviewBrokerTests` publishes JSON straight to the broker (not via `publishEvent`); the consumer moves a seeded `PENDING` row to `READY`.
4. Compose broker demo: `POST` of a reachable HTTP target produced `PENDING` on `app1` and `preview ready` on `app2` (`hash` `1003`, status `READY`, title `Compose Story`). An unreachable HTTPS target (`example.com`) produced `FAILED` with detail `Could not fetch …`, which still proves broker delivery.

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
