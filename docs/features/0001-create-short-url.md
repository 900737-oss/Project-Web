# Feature: Create short URL

## Decision

**Owner module**: `links` | **Event-coupled**: Y (`ShortUrlCreatedEvent`)

Accept a target URL, validate it, mint a new base62 code from `short_url_seq`, persist a `ShortUrl`, and publish `ShortUrlCreatedEvent` so other modules can react.

**Traps**: invalid URLs must be rejected before minting; the same URL gets a new code on every create; the sequence must live in the database so restarts and both app replicas do not repeat codes.

**Technology hints**: `CommonsUrlValidator`; `short_url_seq` starting at `62^3` (4-character codes); `Base62`; Spring Modulith application event. See [ADR 0003](../adr/0003-sequence-short-codes.md).

---

- **Weight:** 0 (seed — not in agreement)
- **Event-coupled:** Y

---

## Later

- **ADR:** [`docs/adr/0001-modulith-and-hexagon.md`](../adr/0001-modulith-and-hexagon.md), [`docs/adr/0002-atomic-upserts.md`](../adr/0002-atomic-upserts.md), [`docs/adr/0003-sequence-short-codes.md`](../adr/0003-sequence-short-codes.md)

### Acceptance criteria

- [x] `POST /api/link` with form `url=` returns **201** and JSON `hash` + location — covered by `LinkFlowTests`
- [x] Invalid URL → **400** — `LinkFlowTests`
- [x] Publishes `ShortUrlCreatedEvent` — implied by stats bootstrap in `LinkFlowTests` + `LinkStatsListener`
- [x] Same URL (also concurrently) mints a distinct code of at least 4 characters — `CreateShortUrlConcurrencyTests`

### Scale evidence

- [x] Level 3 — in-process events via `ShortUrlCreatedEvent`
- [x] `docker compose --profile load run --rm k6` on 2026-10-17: 5 VUs, 30s, 640 iterations, 1280 checks, 0 failed, `http_req_failed` 0%. Exit code 0.

### Qualities (self-assessed)

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | **5** | `LinkFlowTests` covers 201, the created event through stats, and invalid URL → 400. |
| **Scalability** | **2** | k6 through the load balancer: 0 failed checks and 0% HTTP failures. |
| **Engineering** | **1.5** | ADR + `ModularityTests` green; `./gradlew check` green; AI disclosure present. |

**Indicative total:** 5 + 2 + 1.5 = **8.5 / 10**.

### AI disclosure

- **Tools / skills:** Model selected by Cursor Auto.
- **Used for:** Adapting the seed feature from 25-26 course code to the 26-27 course code.
- **Cursor agent (Grok):** sequence-backed base62 codes (`short_url_seq` from `62^3`), one new code per create, Guava removed.
- **Human-reviewed:** The instructor reviewed the code and verified the correctness of the adaptation.
