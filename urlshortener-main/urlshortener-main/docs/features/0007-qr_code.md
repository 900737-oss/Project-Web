# Feature: QR Code Generation

## Decision (required by 2 October)

**Owner module**: `links` | **Event-coupled**: N

Generates a QR code PNG (or SVG) for any shortened URL on demand via content negotiation (Accept: image/png). Optionally embedded as a Base64 field in the creation response.

**Traps**: large QR payloads generated synchronously on every redirect poll; content negotiation
conflicts with other features that extend the creation response; image format mismatch between
on-the-fly generation and cached responses.

**Technology hints**: ZXing (core + javase); @RequestMapping(produces = "image/png");
Spring StreamingResponseBody for large images.

---

- **Weight:** 8 
- **Event-coupled:** N

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
