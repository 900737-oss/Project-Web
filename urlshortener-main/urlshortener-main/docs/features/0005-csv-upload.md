# Feature: CSV Upload

## Decision

**Owner module**: `links` | **Event-coupled**: Y  (each row emits `ShortUrlCreatedEvent`)

Enables batch URL shortening: users upload a CSV of URLs and receive a CSV of shortened URLs.
Each row is processed individually; a single failure does not abort the batch. The response is a
CSV with the same columns plus shortUrl and status.

**Traps**: RFC 4180 edge cases (quoted commas, embedded newlines); row-level vs request
level error handling. Claiming Level 3 (weight 13) requires a new event of your own; re-emitting
ShortUrlCreatedEvent does not count.

**Technology hints**: Apache Commons CSV or OpenCSV; multipart/form-data.

---

- **Weight:** 13
- **Event-coupled:** Y

---

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] A multipart CSV of URLs returns a CSV with the same columns plus `shortUrl` and `status`.
- [ ] One invalid row is reported in its own status and does not abort the rest of the batch.
- [ ] Quoted commas and embedded newlines are accepted as RFC 4180.
- [ ] Each successful row persists a short URL. Weight 13 still needs a new event type owned by the team; publishing only `ShortUrlCreatedEvent` does not reach Level 3.

### Scale evidence

Not delivered in the 27 November prototype. The delivered Level 4 feature is [0006](0006-redirect_cache_cross_replica.md). The shared k6 run on 2026-10-17 had 0 failed checks.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 0 | No CSV upload endpoint exists yet. |
| **Scalability** | 0 | No CSV-specific run. The product k6 run does not exercise this feature. |
| **Engineering** | 0 | No ADR for this feature. The feature is not implemented, so `./gradlew check` does not cover it. |

**Indicative total:** 0 / 10

### AI disclosure

- **Tools / skills:** Cursor agent.
- **Used for:** writing these acceptance criteria. The feature itself is not implemented.
- **Human-reviewed:** pending.
