# Feature cards (agreement)

Seed cards `0001`–`0003` are provided and ignored in the agreement. Teams add **grown** cards from `0004-…` upward.

## How to create a card (October 2)

1. Pick a feature from the [Feature Catalogue](https://moodle.unizar.es/add/mod/resource/view.php?id=12167300), or write a new catalogue entry in the same shape.
2. Copy [`0000-TEMPLATE.md`](0000-TEMPLATE.md) → `NNNN-short-title.md`.
3. Paste the catalogue entry into `## Decision` and choose a **weight** (5, 8, 13, or 21).
4. Leave `## Later` empty — fill it before the feature is graded.
5. Link the card in [`AGREEMENT.md`](../../AGREEMENT.md).

Personal names and ownership belong in your local `TEAM.md` (git-ignored), not in the card.

## Rules (locked)

- Weight ∈ {**5, 8, 13, 21**} (Fibonacci = ExpectedLevel 1–4)
- **Σ weights = 84** (spend the full budget)
- **≥ 4** grown features (one per member of a team of 4); **≥ 2** event-coupled
- One card per file: `NNNN-short-title.md`
- Owner module must match `AGENTS.md` (or a new module + ADR)
- ADR only when the feature **locks** a decision — see [`../adr/README.md`](../adr/README.md)
- **AI disclosure** and **`./gradlew check` green** are required before grading (not for the agreement)

## Seed (provided, not in agreement)

| ID | Feature | Owner | Event-coupled | Tests |
| --- | --- | --- | --- | --- |
| [0001](0001-create-short-url.md) | Create short URL | `links` | `ShortUrlCreatedEvent` | `POST /api/link`, `LinkFlowTests`, `CreateShortUrlConcurrency(Postgres)Tests` |
| [0002](0002-redirect-and-click-log.md) | Redirect and click log | `links` + `clicks` | `ClickLoggedEvent` | `GET /{hash}`, `LinkFlowTests`, `RecordClickIdempotency(Postgres)Tests` |
| [0003](0003-link-stats.md) | Link stats | `analytics` | `ShortUrlCreatedEvent`, `ClickLoggedEvent` | `GET /api/stats/{hash}`, `LinkFlowTests`, `LinkStatsConcurrency(Postgres)Tests`, `ProcessedClickEventPrune(Postgres)Tests` |

## Grown (index)

| ID | Feature | Owner module | Weight | Event-coupled | ADR |
| --- | --- | --- | --- | --- | --- |
| [0004](0004-link_preview_open_graph.md) | Link preview / Open Graph metadata | `preview` | 21 | `ShortUrlCreatedEvent`, `LinkPreviewRequestedEvent` | [0005](../adr/0005-link-preview-broker.md) |
| [0005](0005-csv-upload.md) | CSV upload | `links` | 13 | `ShortUrlCreatedEvent` | — |
| [0006](0006-redirect_cache_cross_replica.md) | Redirect cache with cross-replica invalidation | `links` | 21 | `ShortUrlUpdatedEvent` | [0004](../adr/0004-redis-redirect-cache.md) |
| [0007](0007-qr_code.md) | QR code generation | `links` | 8 | No | — |
| [0008](0008-async_click_ingestion.md) | Async click ingestion | `clicks` | 21 | `ClickLoggedEvent` | — |

**Budget used:** 0 / 84 · **Grown:** 0 / ≥4 · **Event-coupled:** 0 / ≥2
