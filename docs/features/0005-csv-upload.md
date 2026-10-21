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
