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

- [ ] `GET /{hash}` with `Accept: image/png` returns a PNG QR code for that short URL.
- [ ] The same request without that `Accept` header still returns 307 to the target.
- [ ] An unknown hash returns 404 for both the redirect and the QR response.
- [ ] QR generation is not executed on the ordinary redirect path.

### Scale evidence

Not delivered in the 27 November prototype. The delivered Level 4 feature is [0006](0006-redirect_cache_cross_replica.md). The shared k6 run on 2026-10-17 had 0 failed checks. This feature is weight 8, so it does not need an event.

### Qualities

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 0 | No QR response exists yet. |
| **Scalability** | 0 | No QR-specific run. The product k6 run does not exercise this feature. |
| **Engineering** | 0 | No ADR is required until a library choice is locked. The feature is not implemented. |

**Indicative total:** 0 / 10

### AI disclosure

- **Tools / skills:** Cursor agent.
- **Used for:** writing these acceptance criteria. The feature itself is not implemented.
- **Human-reviewed:** pending.
